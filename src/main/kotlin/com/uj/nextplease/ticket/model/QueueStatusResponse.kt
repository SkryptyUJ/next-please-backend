package com.uj.nextplease.ticket.model

data class QueueStatusResponse(
    val ticketNumber: String,
    val status: TicketStatus,
    val type: TicketType,
    val positionInQueue: Int,
    val queueSize: Int,
    val roomId: Long?,
)
