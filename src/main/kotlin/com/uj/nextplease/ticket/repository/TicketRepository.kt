package com.uj.nextplease.ticket.repository

import com.uj.nextplease.ticket.Ticket
import org.springframework.data.jpa.repository.JpaRepository

interface TicketRepository : JpaRepository<Ticket, Long> {
    fun findByTicketName(ticketName: String): Ticket?
}
