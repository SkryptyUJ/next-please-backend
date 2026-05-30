package com.uj.nextplease.ticket.controller

import com.uj.nextplease.security.JwtService
import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketCreateResponse
import com.uj.nextplease.ticket.service.TicketService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tickets")
class TicketController(
    private val ticketService: TicketService,
    private val jwtService: JwtService,
) {
    @PostMapping("/create")
    fun createTicket(
        @RequestBody request: TicketCreateRequest,
    ): ResponseEntity<TicketCreateResponse> =
        try {
            val ticketResponse = ticketService.createTicket(request)
            val token = jwtService.generatePatientToken(ticketResponse.ticketNumber)

            ResponseEntity.ok(
                TicketCreateResponse(
                    ticketNumber = ticketResponse.ticketNumber,
                    token = token,
                    roomId = ticketResponse.roomId,
                ),
            )
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

    @GetMapping("/status/{ticketId}")
    fun getTicketStatus(
        @PathVariable ticketId: String,
    ): ResponseEntity<QueueStatusResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? String

        if (principal != ticketId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val status =
            ticketService.getQueueStatus(ticketId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(status)
    }
}
