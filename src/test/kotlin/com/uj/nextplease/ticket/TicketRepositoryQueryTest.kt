package com.uj.nextplease.ticket

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.repository.TicketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.Date

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class TicketRepositoryQueryTest(
    @Autowired private val ticketRepository: TicketRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        ticketRepository.deleteAllInBatch()
    }

    @Test
    fun `given mixed statuses when findByStatus then only matching tickets are returned`() {
        ticketRepository.save(ticket("C-001", TicketStatus.WAITING, TicketType.CONSULTATION, Date()))
        ticketRepository.save(ticket("K-001", TicketStatus.CALLED, TicketType.CHECKUP, Date()))

        val waiting = ticketRepository.findByStatus(TicketStatus.WAITING)

        assertThat(waiting).extracting("ticketName").containsExactly("C-001")
    }

    @Test
    fun `given waiting tickets of a type when findWaitingByTypeOrderedByCreatedAt then oldest first and filtered by type`() {
        val now = System.currentTimeMillis()
        val first = ticketRepository.save(ticket("C-001", TicketStatus.WAITING, TicketType.CONSULTATION, Date(now - 2000)))
        val second = ticketRepository.save(ticket("C-002", TicketStatus.WAITING, TicketType.CONSULTATION, Date(now - 1000)))
        ticketRepository.save(ticket("K-001", TicketStatus.WAITING, TicketType.CHECKUP, Date()))

        val waiting = ticketRepository.findWaitingByTypeOrderedByCreatedAt(TicketType.CONSULTATION)

        assertThat(waiting).extracting("ticketName").containsExactly(first.ticketName, second.ticketName)
    }

    @Test
    fun `given waiting and called tickets of a type when countWaitingByType then only waiting are counted`() {
        ticketRepository.save(ticket("C-001", TicketStatus.WAITING, TicketType.CONSULTATION, Date()))
        ticketRepository.save(ticket("C-002", TicketStatus.WAITING, TicketType.CONSULTATION, Date()))
        ticketRepository.save(ticket("C-003", TicketStatus.CALLED, TicketType.CONSULTATION, Date()))
        ticketRepository.save(ticket("K-001", TicketStatus.WAITING, TicketType.CHECKUP, Date()))

        val count = ticketRepository.countWaitingByType(TicketType.CONSULTATION)

        assertThat(count).isEqualTo(2)
    }

    @Test
    @Transactional
    fun `given waiting tickets of a type when findOldestWaitingByTypeForUpdate limited to one then it returns the oldest`() {
        val now = System.currentTimeMillis()
        val oldest = ticketRepository.save(ticket("C-001", TicketStatus.WAITING, TicketType.CONSULTATION, Date(now - 2000)))
        ticketRepository.save(ticket("C-002", TicketStatus.WAITING, TicketType.CONSULTATION, Date(now - 1000)))

        val result = ticketRepository.findOldestWaitingByTypeForUpdate(TicketType.CONSULTATION, PageRequest.of(0, 1))

        assertThat(result).extracting("ticketName").containsExactly(oldest.ticketName)
    }

    private fun ticket(
        name: String,
        status: TicketStatus,
        type: TicketType,
        createdAt: Date,
    ): Ticket =
        Ticket(
            ticketName = name,
            status = status,
            createdAt = createdAt,
            roomId = null,
            doctorId = null,
            type = type,
        )
}
