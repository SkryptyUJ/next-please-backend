package com.uj.nextplease.ticket

import org.springframework.data.jpa.repository.JpaRepository

interface TicketRepository : JpaRepository<Ticket, Long> {
    fun findByTicketName(ticketName: String): Ticket?
}
