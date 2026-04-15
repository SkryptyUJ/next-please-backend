package com.uj.nextplease.ticket.service

import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.repository.TicketRepository
import org.springframework.stereotype.Service

@Service
class TicketService(
    private val ticketRepository: TicketRepository,
) {
    fun findByTicketName(ticketName: String): TicketDetails? {
        val ticket = ticketRepository.findByTicketName(ticketName)
        return toTicketDetails(ticket ?: return null)
    }

    private fun toTicketDetails(ticket: Ticket): TicketDetails =
        TicketDetails(
            id = ticket.id!!,
            ticketName = ticket.ticketName!!,
            status = ticket.status!!,
            createdAt = ticket.createdAt!!,
            calledAt = ticket.calledAt,
            roomId = ticket.roomId,
            doctorId = ticket.doctorId,
        )
}
