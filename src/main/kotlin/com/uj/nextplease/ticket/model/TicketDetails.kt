package com.uj.nextplease.ticket.model

import java.util.Date

data class TicketDetails(
    val id: Long,
    val ticketName: String,
    val status: String,
    val createdAt: Date,
    val calledAt: Date? = null,
    val roomId: Long? = null,
    val doctorId: Long? = null,
)
