package com.uj.nextplease.ticket.model

data class TicketCreateRequest(
    val type: TicketType,
    val roomId: Long,
)
