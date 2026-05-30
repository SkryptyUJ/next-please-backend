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
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Date

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
    fun `createTicket persists a waiting ticket with the requested type and room`() {
        val room = roomRepository.save(Room(name = "Room A", isActive = true))

        val response =
            ticketService.createTicket(
                TicketCreateRequest(
                    type = TicketType.CONSULTATION,
                    roomId = room.id!!,
                ),
            )

        val saved = ticketRepository.findByTicketName(response.ticketNumber)

        assertThat(response.ticketNumber).startsWith("R-")
        assertThat(saved).isNotNull()
        assertThat(saved?.status).isEqualTo(TicketStatus.WAITING)
        assertThat(saved?.type).isEqualTo(TicketType.CONSULTATION)
        assertThat(saved?.roomId).isEqualTo(room.id)
    }

    @Test
    fun `getQueueStatus returns queue position and size`() {
        val room = roomRepository.save(Room(name = "Room B", isActive = true))

        val first =
            ticketRepository.save(
                Ticket(
                    ticketName = "B-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(System.currentTimeMillis() - 1000),
                    roomId = room.id,
                    type = TicketType.CONSULTATION,
                ),
            )
        ticketRepository.save(
            Ticket(
                ticketName = "B-002",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CHECKUP,
            ),
        )

        val status = ticketService.getQueueStatus(first.ticketName!!)

        assertThat(status).isNotNull()
        assertThat(status?.positionInQueue).isEqualTo(1)
        assertThat(status?.queueSize).isEqualTo(2)
        assertThat(status?.type).isEqualTo(TicketType.CONSULTATION)
        assertThat(status?.roomId).isEqualTo(room.id)
    }

    @Test
    fun `getNextPatientByType returns the oldest ticket of the requested type`() {
        val room = roomRepository.save(Room(name = "Room C", isActive = true))

        val olderConsultation =
            ticketRepository.save(
                Ticket(
                    ticketName = "C-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(System.currentTimeMillis() - 2000),
                    roomId = room.id,
                    type = TicketType.CONSULTATION,
                ),
            )
        ticketRepository.save(
            Ticket(
                ticketName = "C-002",
                status = TicketStatus.WAITING,
                createdAt = Date(System.currentTimeMillis() - 1000),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "C-003",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.URGENT,
            ),
        )

        val next = ticketService.getNextPatientByType(room.id!!, TicketType.CONSULTATION)

        assertThat(next).isNotNull()
        assertThat(next?.ticketName).isEqualTo(olderConsultation.ticketName)
        assertThat(next?.type).isEqualTo(TicketType.CONSULTATION)
    }

    @Test
    fun `callPatient marks ticket as called and notifies queue`() {
        val room = roomRepository.save(Room(name = "Room D", isActive = true))
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "D-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(),
                    roomId = room.id,
                    type = TicketType.URGENT,
                ),
            )

        val called = ticketService.callPatient(ticket.id!!, room.id!!)
        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()

        assertThat(called).isNotNull()
        assertThat(reloaded.status).isEqualTo(TicketStatus.CALLED)
        assertThat(reloaded.calledAt).isNotNull()
        verify(queueService).broadcastPatientCalled(eq(room.id!!), eq(ticket.ticketName!!), eq(room.name))
    }

    @Test
    fun `cancelTicket marks ticket as cancelled`() {
        val room = roomRepository.save(Room(name = "Room E", isActive = true))
        val ticket =
            ticketRepository.save(
                Ticket(
                    ticketName = "E-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(),
                    roomId = room.id,
                    type = TicketType.CHECKUP,
                ),
            )

        val cancelled = ticketService.cancelTicket(ticket.id!!)
        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()

        assertThat(cancelled).isNotNull()
        assertThat(reloaded.status).isEqualTo(TicketStatus.CANCELLED)
    }

    @Test
    fun `getAvailableTypes returns distinct waiting ticket types in queue order`() {
        val room = roomRepository.save(Room(name = "Room F", isActive = true))
        ticketRepository.save(
            Ticket(
                ticketName = "F-001",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "F-002",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "F-003",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CHECKUP,
            ),
        )

        val types = ticketService.getAvailableTypes(room.id!!)

        assertThat(types).containsExactly(TicketType.CONSULTATION, TicketType.CHECKUP)
    }
}
