package com.uj.nextplease.util

import kotlin.math.pow

object Constants {
    // SSE
    const val SSE_EVENT_QUEUE_UPDATE = "queue-update"
    const val SSE_EVENT_PATIENT_CALLED = "patient-called"
    const val SSE_DATA_TICKET_NUMBER = "ticketNumber"
    const val SSE_DATA_ROOM_NUMBER = "roomNumber"
    const val SSE_DATA_VISIT_ENDS_AT = "visitEndsAt"
    const val SSE_TIMEOUT_MS: Long = 60_000L

    // Roles
    const val ROLE_PATIENT = "PATIENT"
    const val ROLE_DOCTOR = "DOCTOR"

    // Visit
    const val VISIT_DURATION_SECONDS = 20

    // Ticket generation
    const val DEFAULT_TICKET_NUMBER_LENGTH = 3
    val DEFAULT_TICKET_RANDOM_MAX: Int = 10.0.pow(DEFAULT_TICKET_NUMBER_LENGTH.toDouble()).toInt()
}
