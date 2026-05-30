package com.uj.nextplease.ticket

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.room.Room
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.repository.TicketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.Date

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class TicketRepositoryQueryTest(
    @Autowired private val ticketRepository: TicketRepository,
    @Autowired private val roomRepository: RoomRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        ticketRepository.deleteAllInBatch()
        roomRepository.deleteAllInBatch()
    }

    @Test
    fun `findByStatus returns only matching tickets`() {
        val room = roomRepository.save(Room(name = "Query Room A", isActive = true))
        ticketRepository.save(
            Ticket(
                ticketName = "QA-001",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "QA-002",
                status = TicketStatus.CALLED,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CHECKUP,
            ),
        )

        val waiting = ticketRepository.findByStatus(TicketStatus.WAITING)

        assertThat(waiting).extracting("ticketName").containsExactly("QA-001")
    }

    @Test
    fun `findAllWaitingOrderedByCreatedAt returns oldest waiting tickets first`() {
        val room = roomRepository.save(Room(name = "Query Room B", isActive = true))
        val older =
            ticketRepository.save(
                Ticket(
                    ticketName = "QB-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(System.currentTimeMillis() - 2000),
                    roomId = room.id,
                    type = TicketType.CONSULTATION,
                ),
            )
        val newer =
            ticketRepository.save(
                Ticket(
                    ticketName = "QB-002",
                    status = TicketStatus.WAITING,
                    createdAt = Date(System.currentTimeMillis() - 1000),
                    roomId = room.id,
                    type = TicketType.CHECKUP,
                ),
            )

        val waiting = ticketRepository.findAllWaitingOrderedByCreatedAt()

        assertThat(waiting).extracting("ticketName").containsExactly(older.ticketName, newer.ticketName)
    }

    @Test
    fun `findWaitingByRoomIdAndTypeOrderedByCreatedAt filters by room and type`() {
        val roomOne = roomRepository.save(Room(name = "Query Room C", isActive = true))
        val roomTwo = roomRepository.save(Room(name = "Query Room D", isActive = true))

        val first =
            ticketRepository.save(
                Ticket(
                    ticketName = "QC-001",
                    status = TicketStatus.WAITING,
                    createdAt = Date(System.currentTimeMillis() - 2000),
                    roomId = roomOne.id,
                    type = TicketType.CONSULTATION,
                ),
            )
        ticketRepository.save(
            Ticket(
                ticketName = "QC-002",
                status = TicketStatus.WAITING,
                createdAt = Date(System.currentTimeMillis() - 1000),
                roomId = roomOne.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "QC-003",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = roomTwo.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "QC-004",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = roomOne.id,
                type = TicketType.CHECKUP,
            ),
        )

        val waiting = ticketRepository.findWaitingByRoomIdAndTypeOrderedByCreatedAt(roomOne.id!!, TicketType.CONSULTATION)

        assertThat(waiting).extracting("ticketName").containsExactly(first.ticketName, "QC-002")
    }

    @Test
    fun `countWaitingByRoomId counts only waiting tickets in room`() {
        val room = roomRepository.save(Room(name = "Query Room E", isActive = true))
        ticketRepository.save(
            Ticket(
                ticketName = "QE-001",
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CONSULTATION,
            ),
        )
        ticketRepository.save(
            Ticket(
                ticketName = "QE-002",
                status = TicketStatus.CALLED,
                createdAt = Date(),
                roomId = room.id,
                type = TicketType.CHECKUP,
            ),
        )

        val count = ticketRepository.countWaitingByRoomId(room.id!!)

        assertThat(count).isEqualTo(1)
    }
}
