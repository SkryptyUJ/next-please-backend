package com.uj.nextplease.ticket

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.room.Room
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.user.User
import com.uj.nextplease.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.Date

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class TicketRepositoryPersistenceTest(
    @Autowired private val ticketRepository: TicketRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRepository: UserRepository,
) {
    @Test
    fun `save ticket and find by ticket name`() {
        val doctor =
            userRepository.save(
                User(
                    email = "ticket-doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "Kate",
                    surname = "Taylor",
                ),
            )

        val room =
            roomRepository.save(
                Room(
                    name = "Room C",
                    isActive = true,
                    doctorId = doctor.id,
                ),
            )

        val saved =
            ticketRepository.save(
                Ticket(
                    ticketName = "T-001",
                    status = "NEW",
                    createdAt = Date(),
                    roomId = room.id,
                    doctorId = doctor.id,
                ),
            )

        val found = ticketRepository.findByTicketName("T-001")

        assertNotNull(saved.id)
        assertNotNull(found)
        assertEquals(found.id, saved.id)
    }
}
