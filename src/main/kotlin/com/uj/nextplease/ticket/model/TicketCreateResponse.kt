package com.uj.nextplease.ticket.model

data class TicketCreateResponse(
    val ticketNumber: String,
    val token: String,
    val roomId: Long,
)
