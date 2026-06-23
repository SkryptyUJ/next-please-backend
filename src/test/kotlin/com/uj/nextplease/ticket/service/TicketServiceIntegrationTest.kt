package com.uj.nextplease.ticket.service

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.queue.service.QueueService
import com.uj.nextplease.room.Room
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.user.User
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class TicketServiceIntegrationTest(
    @Autowired private val ticketService: TicketService,
    @Autowired private val ticketRepository: TicketRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRepository: UserRepository,
) {
    @MockitoBean
    private lateinit var queueService: QueueService

    @BeforeEach
    fun cleanDatabase() {
        ticketRepository.deleteAllInBatch()
        roomRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `given a type when createTicket then it persists a waiting ticket with no room or doctor`() {
        val response = ticketService.createTicket(TicketCreateRequest(type = TicketType.CONSULTATION))

        val saved = ticketRepository.findByTicketName(response.ticketNumber)

        assertThat(saved).isNotNull()
        assertThat(saved?.status).isEqualTo(TicketStatus.WAITING)
        assertThat(saved?.type).isEqualTo(TicketType.CONSULTATION)
        assertThat(saved?.roomId).isNull()
        assertThat(saved?.doctorId).isNull()
    }

    @Test
    fun `given each type when createTicket then ticket number uses the type prefix`() {
        val consultation = ticketService.createTicket(TicketCreateRequest(type = TicketType.CONSULTATION))
        val checkup = ticketService.createTicket(TicketCreateRequest(type = TicketType.CHECKUP))
        val urgent = ticketService.createTicket(TicketCreateRequest(type = TicketType.URGENT))

        assertThat(consultation.ticketNumber).matches("C-\\d{3}")
        assertThat(checkup.ticketNumber).matches("K-\\d{3}")
        assertThat(urgent.ticketNumber).matches("U-\\d{3}")
    }

    @Test
    fun `given waiting tickets of several types when getQueueStatus then position and size are computed per type`() {
        val firstConsultation =
            ticketRepository.save(
                waitingTicket("C-001", TicketType.CONSULTATION, System.currentTimeMillis() - 3000),
            )
        ticketRepository.save(
            waitingTicket("C-002", TicketType.CONSULTATION, System.currentTimeMillis() - 2000),
        )
        ticketRepository.save(
            waitingTicket("K-001", TicketType.CHECKUP, System.currentTimeMillis() - 1000),
        )

        val status = ticketService.getQueueStatus(firstConsultation.ticketName!!)

        assertThat(status).isNotNull()
        assertThat(status?.positionInQueue).isEqualTo(1)
        assertThat(status?.queueSize).isEqualTo(2)
        assertThat(status?.type).isEqualTo(TicketType.CONSULTATION)
    }

    @Test
    fun `given waiting tickets when getAvailableTypes then it returns distinct types globally`() {
        ticketRepository.save(waitingTicket("C-001", TicketType.CONSULTATION, System.currentTimeMillis() - 3000))
        ticketRepository.save(waitingTicket("C-002", TicketType.CONSULTATION, System.currentTimeMillis() - 2000))
        ticketRepository.save(waitingTicket("K-001", TicketType.CHECKUP, System.currentTimeMillis() - 1000))

        val types = ticketService.getAvailableTypes()

        assertThat(types).containsExactly(TicketType.CONSULTATION, TicketType.CHECKUP)
    }

    @Test
    fun `given a waiting patient when pairNextPatient then the oldest is called and assigned a room and doctor`() {
        val doctor = userRepository.save(doctor("pair-doctor@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))

        val oldest = ticketRepository.save(waitingTicket("C-001", TicketType.CONSULTATION, System.currentTimeMillis() - 2000))
        ticketRepository.save(waitingTicket("C-002", TicketType.CONSULTATION, System.currentTimeMillis() - 1000))

        val visit = ticketService.pairNextPatient(TicketType.CONSULTATION, room.id!!, room.name, doctor.id!!)

        assertThat(visit).isNotNull()
        assertThat(visit?.ticketName).isEqualTo(oldest.ticketName)

        val reloaded = ticketRepository.findById(oldest.id!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(TicketStatus.CALLED)
        assertThat(reloaded.calledAt).isNotNull()
        assertThat(reloaded.roomId).isEqualTo(room.id)
        assertThat(reloaded.doctorId).isEqualTo(doctor.id)
    }

    @Test
    fun `given no waiting patient of the type when pairNextPatient then it returns null`() {
        val doctor = userRepository.save(doctor("empty-doctor@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        ticketRepository.save(waitingTicket("K-001", TicketType.CHECKUP, System.currentTimeMillis()))

        val visit = ticketService.pairNextPatient(TicketType.URGENT, room.id!!, room.name, doctor.id!!)

        assertThat(visit).isNull()
    }

    @Test
    fun `given two doctors pairing the same single patient concurrently when pairNextPatient then only one wins`() {
        val doctorOne = userRepository.save(doctor("race-one@clinic.com"))
        val doctorTwo = userRepository.save(doctor("race-two@clinic.com"))
        val roomOne = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctorOne.id))
        val roomTwo = roomRepository.save(Room(name = "Room B", isActive = true, doctorId = doctorTwo.id))
        ticketRepository.save(waitingTicket("U-001", TicketType.URGENT, System.currentTimeMillis()))

        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val task = { room: Room, doctorId: Long ->
            Callable {
                barrier.await()
                ticketService.pairNextPatient(TicketType.URGENT, room.id!!, room.name, doctorId)
            }
        }

        val results: List<Future<*>> =
            listOf(
                executor.submit(task(roomOne, doctorOne.id!!)),
                executor.submit(task(roomTwo, doctorTwo.id!!)),
            )
        val visits = results.map { it.get() }
        executor.shutdown()

        assertThat(visits.count { it != null }).isEqualTo(1)
        assertThat(ticketRepository.findByStatus(TicketStatus.CALLED)).hasSize(1)
    }

    @Test
    fun `given a called ticket when completeTicket by the session doctor then it becomes completed`() {
        val doctor = userRepository.save(doctor("complete-doctor@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "U-001",
                    status = TicketStatus.CALLED,
                    createdAt = Date(),
                    calledAt = Date(),
                    roomId = room.id,
                    doctorId = doctor.id,
                    type = TicketType.URGENT,
                ),
            )

        val completed = ticketService.completeTicket(ticket.id!!, doctor.id!!)

        assertThat(completed?.status).isEqualTo(TicketStatus.COMPLETED)
        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(TicketStatus.COMPLETED)
    }

    @Test
    fun `given a called ticket when completeTicket by another doctor then it throws AccessDeniedException`() {
        val doctor = userRepository.save(doctor("complete-doctor2@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "U-001",
                    status = TicketStatus.CALLED,
                    createdAt = Date(),
                    calledAt = Date(),
                    roomId = room.id,
                    doctorId = doctor.id,
                    type = TicketType.URGENT,
                ),
            )

        assertThatThrownBy { ticketService.completeTicket(ticket.id!!, doctor.id!! + 1) }
            .isInstanceOf(AccessDeniedException::class.java)

        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(TicketStatus.CALLED)
    }

    @Test
    fun `given a non-called ticket when completeTicket then it throws IllegalStateException`() {
        val doctor = userRepository.save(doctor("complete-doctor3@clinic.com"))
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "U-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(),
                    roomId = null,
                    doctorId = doctor.id,
                    type = TicketType.URGENT,
                ),
            )

        assertThatThrownBy { ticketService.completeTicket(ticket.id!!, doctor.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given a non-existent ticket id when completeTicket then it returns null`() {
        val result = ticketService.completeTicket(Long.MAX_VALUE, 1L)

        assertThat(result).isNull()
    }

    @Test
    fun `given a called ticket when getQueueStatus then position is zero`() {
        val doctor = userRepository.save(doctor("status-doctor@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        ticketRepository.save(
            Ticket(
                ticketName = "C-001",
                status = TicketStatus.CALLED,
                createdAt = Date(),
                calledAt = Date(),
                roomId = room.id,
                doctorId = doctor.id,
                type = TicketType.CONSULTATION,
            ),
        )

        val status = ticketService.getQueueStatus("C-001")

        assertThat(status).isNotNull()
        assertThat(status?.positionInQueue).isEqualTo(0)
    }

    @Test
    fun `given no ticket with that name when getQueueStatus then it returns null`() {
        val status = ticketService.getQueueStatus("DOES-NOT-EXIST")

        assertThat(status).isNull()
    }

    @Test
    fun `given no waiting tickets when getAvailableTypes then it returns an empty list`() {
        val types = ticketService.getAvailableTypes()

        assertThat(types).isEmpty()
    }

    @Test
    fun `given a non-existent ticket when cancelTicket then it throws NoSuchElementException`() {
        assertThatThrownBy { ticketService.cancelTicket("DOES-NOT-EXIST") }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `given a waiting ticket when cancelTicket then it becomes cancelled`() {
        val ticket = ticketRepository.save(waitingTicket("C-001", TicketType.CONSULTATION, System.currentTimeMillis()))

        val cancelled = ticketService.cancelTicket(ticket.ticketName!!)

        assertThat(cancelled.status).isEqualTo(TicketStatus.CANCELLED)
        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(TicketStatus.CANCELLED)
    }

    @Test
    fun `given a non-waiting ticket when cancelTicket then it throws IllegalStateException`() {
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "C-001",
                    status = TicketStatus.CALLED,
                    createdAt = Date(),
                    calledAt = Date(),
                    type = TicketType.CONSULTATION,
                ),
            )

        assertThatThrownBy { ticketService.cancelTicket(ticket.ticketName!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun waitingTicket(
        name: String,
        type: TicketType,
        createdAtMillis: Long,
    ): Ticket =
        Ticket(
            ticketName = name,
            status = TicketStatus.WAITING,
            createdAt = Date(createdAtMillis),
            roomId = null,
            doctorId = null,
            type = type,
        )

    private fun doctor(email: String): User =
        User(
            email = email,
            password = "encoded",
            role = "DOCTOR",
            name = "Doc",
            surname = "Tor",
        )
}
