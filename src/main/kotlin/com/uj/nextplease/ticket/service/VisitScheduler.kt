package com.uj.nextplease.ticket.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class VisitScheduler(
    private val ticketService: TicketService,
) {
    @Scheduled(fixedDelay = 1000)
    fun completeExpiredVisits() {
        ticketService.completeExpiredVisits()
    }
}
