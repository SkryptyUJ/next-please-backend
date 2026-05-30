package com.uj.nextplease.room.service

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.DoctorAssignmentRequest
import com.uj.nextplease.room.model.RoomUpdateRequest
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
import java.util.Date

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class RoomServiceIntegrationTest(
    @Autowired private val roomService: RoomService,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val ticketRepository: TicketRepository,
    @Autowired private val userRepository: UserRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        ticketRepository.deleteAllInBatch()
        roomRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `createRoom persists a new room and getAllRooms returns it`() {
        val created = roomService.createRoom("Room A")

        val rooms = roomService.getAllRooms()

        assertThat(created.id).isNotNull()
        assertThat(created.name).isEqualTo("Room A")
        assertThat(created.isActive).isTrue()
        assertThat(rooms).extracting("name").containsExactly("Room A")
    }

    @Test
    fun `updateRoom changes name and active flag`() {
        val room = roomRepository.save(Room(name = "Old Room", isActive = true))

        val updated =
            roomService.updateRoom(
                room.id!!,
                RoomUpdateRequest(name = "New Room", isActive = false),
            )

        assertThat(updated).isNotNull()
        assertThat(updated?.name).isEqualTo("New Room")
        assertThat(updated?.isActive).isFalse()
    }

    @Test
    fun `getActiveRooms returns only active rooms`() {
        roomRepository.save(Room(name = "Active Room", isActive = true))
        roomRepository.save(Room(name = "Inactive Room", isActive = false))

        val activeRooms = roomService.getActiveRooms()

        assertThat(activeRooms).hasSize(1)
        assertThat(activeRooms.first().name).isEqualTo("Active Room")
    }

    @Test
    fun `assignDoctorToRoom sets doctor and returns doctor info`() {
        val doctor =
            userRepository.save(
                User(
                    email = "doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "Anna",
                    surname = "Kowalska",
                ),
            )
        val room = roomRepository.save(Room(name = "Room B", isActive = true))
        ticketRepository.save(
            Ticket(
                ticketName = "B-001",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )

        val assigned = roomService.assignDoctorToRoom(room.id!!, DoctorAssignmentRequest(doctor.id))

        assertThat(assigned).isNotNull()
        assertThat(assigned?.doctorId).isEqualTo(doctor.id)
        assertThat(assigned?.doctorName).isEqualTo("Anna")
        assertThat(assigned?.doctorSurname).isEqualTo("Kowalska")
        assertThat(assigned?.waitingQueueSize).isEqualTo(1)
    }

    @Test
    fun `assignDoctorToRoom prevents assigning the same doctor to multiple rooms`() {
        val doctor =
            userRepository.save(
                User(
                    email = "duplicate-doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "Piotr",
                    surname = "Nowak",
                ),
            )
        val firstRoom = roomRepository.save(Room(name = "Room C", isActive = true))
        val secondRoom = roomRepository.save(Room(name = "Room D", isActive = true))

        roomService.assignDoctorToRoom(firstRoom.id!!, DoctorAssignmentRequest(doctor.id))

        assertThatThrownBy {
            roomService.assignDoctorToRoom(secondRoom.id!!, DoctorAssignmentRequest(doctor.id))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already assigned")
    }

    @Test
    fun `assignDoctorToRoom can unassign a doctor`() {
        val doctor =
            userRepository.save(
                User(
                    email = "unassign-doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "Marta",
                    surname = "Zielinska",
                ),
            )
        val room = roomRepository.save(Room(name = "Room E", isActive = true, doctorId = doctor.id))

        val updated = roomService.assignDoctorToRoom(room.id!!, DoctorAssignmentRequest(null))

        assertThat(updated?.doctorId).isNull()
    }
}
