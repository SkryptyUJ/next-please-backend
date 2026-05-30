package com.uj.nextplease.ticket.service

import com.uj.nextplease.queue.service.QueueService
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.repository.TicketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

class TicketServiceTest {
    private lateinit var ticketRepository: TicketRepository
    private lateinit var roomRepository: RoomRepository
    private lateinit var queueService: QueueService
    private lateinit var ticketService: TicketService

    @BeforeEach
    fun setUp() {
        ticketRepository = mock()
        roomRepository = mock()
        queueService = mock()
        ticketService = TicketService(ticketRepository, roomRepository, queueService)
    }

    @Test
    fun `findByTicketName returns ticket details when ticket exists`() {
        val now = Date()
        val ticket =
            Ticket(
                id = 1L,
                ticketName = "T-001",
                status = TicketStatus.WAITING,
                createdAt = now,
                calledAt = null,
                roomId = 1L,
                doctorId = 1L,
                type = TicketType.CONSULTATION,
            )
        whenever(ticketRepository.findByTicketName("T-001")).thenReturn(ticket)

        val result = ticketService.findByTicketName("T-001")

        assertThat(result).isNotNull()
        assertThat(result?.ticketName).isEqualTo("T-001")
        assertThat(result?.status).isEqualTo(TicketStatus.WAITING)
        assertThat(result?.roomId).isEqualTo(1L)
        assertThat(result?.doctorId).isEqualTo(1L)
    }

    @Test
    fun `findByTicketName returns null when ticket does not exist`() {
        whenever(ticketRepository.findByTicketName("INVALID")).thenReturn(null)

        val result = ticketService.findByTicketName("INVALID")

        assertThat(result).isNull()
    }

    @Test
    fun `findByTicketName maps all ticket fields correctly`() {
        val now = Date()
        val calledAt = Date(now.time + 60000)
        val ticket =
            Ticket(
                id = 42L,
                ticketName = "T-123",
                status = TicketStatus.CALLED,
                createdAt = now,
                calledAt = calledAt,
                roomId = 5L,
                doctorId = 10L,
                type = TicketType.URGENT,
            )
        whenever(ticketRepository.findByTicketName("T-123")).thenReturn(ticket)

        val result = ticketService.findByTicketName("T-123")

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(42L)
        assertThat(result.ticketName).isEqualTo("T-123")
        assertThat(result.status).isEqualTo(TicketStatus.CALLED)
        assertThat(result.createdAt).isEqualTo(now)
        assertThat(result.calledAt).isEqualTo(calledAt)
        assertThat(result.roomId).isEqualTo(5L)
        assertThat(result.doctorId).isEqualTo(10L)
    }

    @Test
    fun `findByTicketName handles null calledAt field`() {
        val now = Date()
        val ticket =
            Ticket(
                id = 1L,
                ticketName = "T-456",
                status = TicketStatus.WAITING,
                createdAt = now,
                calledAt = null,
                roomId = 2L,
                doctorId = 3L,
                type = TicketType.CHECKUP,
            )
        whenever(ticketRepository.findByTicketName("T-456")).thenReturn(ticket)

        val result = ticketService.findByTicketName("T-456")

        assertThat(result).isNotNull()
        assertThat(result?.calledAt).isNull()
    }

    @Test
    fun `findByTicketName handles different ticket statuses`() {
        val statuses = listOf(TicketStatus.WAITING, TicketStatus.CALLED, TicketStatus.COMPLETED, TicketStatus.CANCELLED)
        val now = Date()

        statuses.forEach { status ->
            val ticket =
                Ticket(
                    id = 1L,
                    ticketName = "T-123",
                    status = status,
                    createdAt = now,
                    roomId = 1L,
                    doctorId = 1L,
                    type = TicketType.CONSULTATION,
                )
            whenever(ticketRepository.findByTicketName("T-123")).thenReturn(ticket)

            val result = ticketService.findByTicketName("T-123")

            assertThat(result?.status).isEqualTo(status)
        }
    }
}
