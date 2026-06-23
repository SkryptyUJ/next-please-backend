package com.uj.nextplease.room.service

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.queue.service.QueueService
import com.uj.nextplease.room.Room
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.Ticket
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

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class RoomServiceIntegrationTest(
    @Autowired private val roomService: RoomService,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val ticketRepository: TicketRepository,
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
    fun `given free and occupied rooms when getAvailableRooms then only free rooms are returned`() {
        val doctor = userRepository.save(doctor("available@clinic.com"))
        roomRepository.save(Room(name = "Free Room", isActive = false, doctorId = null))
        roomRepository.save(Room(name = "Taken Room", isActive = true, doctorId = doctor.id))

        val available = roomService.getAvailableRooms()

        assertThat(available).extracting("name").containsExactly("Free Room")
    }

    @Test
    fun `given a free room when claimRoom then the doctor is seated and the room is active`() {
        val doctor = userRepository.save(doctor("claim@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = false, doctorId = null))

        val claimed = roomService.claimRoom(room.id!!, doctor.id!!)

        assertThat(claimed?.doctorId).isEqualTo(doctor.id)
        assertThat(claimed?.isActive).isTrue()
        val reloaded = roomRepository.findById(room.id!!).orElseThrow()
        assertThat(reloaded.doctorId).isEqualTo(doctor.id)
        assertThat(reloaded.isActive).isTrue()
    }

    @Test
    fun `given a room already taken when claimRoom then it throws IllegalStateException`() {
        val seated = userRepository.save(doctor("seated@clinic.com"))
        val newcomer = userRepository.save(doctor("newcomer@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = seated.id))

        assertThatThrownBy { roomService.claimRoom(room.id!!, newcomer.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given a doctor already seated elsewhere when claimRoom then it throws IllegalStateException`() {
        val doctor = userRepository.save(doctor("busy@clinic.com"))
        roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        val other = roomRepository.save(Room(name = "Room B", isActive = false, doctorId = null))

        assertThatThrownBy { roomService.claimRoom(other.id!!, doctor.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given a claimed room when releaseRoom then it returns to the free pool`() {
        val doctor = userRepository.save(doctor("release@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))

        val released = roomService.releaseRoom(room.id!!, doctor.id!!)

        assertThat(released?.doctorId).isNull()
        assertThat(released?.isActive).isFalse()
        assertThat(roomService.getAvailableRooms()).extracting("name").contains("Room A")
    }

    @Test
    fun `given an in-progress visit when releaseRoom then the called ticket is completed first`() {
        val doctor = userRepository.save(doctor("inprogress@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))
        val ticket =
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

        roomService.releaseRoom(room.id!!, doctor.id!!)

        val reloaded = ticketRepository.findById(ticket.id!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(TicketStatus.COMPLETED)
    }

    @Test
    fun `given a room owned by another doctor when releaseRoom then it throws AccessDeniedException`() {
        val owner = userRepository.save(doctor("owner@clinic.com"))
        val intruder = userRepository.save(doctor("intruder@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = owner.id))

        assertThatThrownBy { roomService.releaseRoom(room.id!!, intruder.id!!) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `given a valid room id when getRoomById then it returns the room response`() {
        val doctor = userRepository.save(doctor("getroom@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))

        val response = roomService.getRoomById(room.id!!)

        assertThat(response).isNotNull()
        assertThat(response?.id).isEqualTo(room.id)
        assertThat(response?.name).isEqualTo("Room A")
        assertThat(response?.doctorId).isEqualTo(doctor.id)
        assertThat(response?.doctorName).isEqualTo("Doc")
    }

    @Test
    fun `given an unknown room id when getRoomById then it returns null`() {
        val response = roomService.getRoomById(Long.MAX_VALUE)

        assertThat(response).isNull()
    }

    @Test
    fun `given a non-existent room when claimRoom then it returns null`() {
        val doctor = userRepository.save(doctor("claimnull@clinic.com"))

        val response = roomService.claimRoom(Long.MAX_VALUE, doctor.id!!)

        assertThat(response).isNull()
    }

    @Test
    fun `given a non-existent room when releaseRoom then it returns null`() {
        val doctor = userRepository.save(doctor("releasenull@clinic.com"))

        val response = roomService.releaseRoom(Long.MAX_VALUE, doctor.id!!)

        assertThat(response).isNull()
    }

    @Test
    fun `given a claimed room with no in-progress tickets when releaseRoom then the room is freed cleanly`() {
        val doctor = userRepository.save(doctor("clean-release@clinic.com"))
        val room = roomRepository.save(Room(name = "Room A", isActive = true, doctorId = doctor.id))

        val released = roomService.releaseRoom(room.id!!, doctor.id!!)

        assertThat(released?.doctorId).isNull()
        assertThat(released?.isActive).isFalse()
    }

    private fun doctor(email: String): User =
        User(
            email = email,
            password = "encoded",
            role = "DOCTOR",
            name = "Doc",
            surname = "Tor",
        )
}
