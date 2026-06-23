package com.uj.nextplease.ticket.model

data class VisitResponse(
    val ticket: TicketDetails,
    val visitEndsAt: String,
)
