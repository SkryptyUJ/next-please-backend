package com.uj.nextplease.ticket.controller

import com.uj.nextplease.security.CorsProperties
import com.uj.nextplease.security.JwtService
import com.uj.nextplease.security.SecurityProperties
import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketCreateResponse
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.service.TicketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Date

class TicketControllerTest {
    private lateinit var ticketService: TicketService
    private lateinit var jwtService: JwtService
    private lateinit var ticketController: TicketController

    @BeforeEach
    fun setUp() {
        ticketService = mock()
        jwtService =
            JwtService(
                SecurityProperties(
                    secretKey = "my-secret-key-that-is-at-least-32-characters-long-for-hmac-sha",
                    staffExpirationMs = 3600000,
                    patientExpirationMs = 1800000,
                    cors =
                        CorsProperties(
                            allowedOrigins = listOf("http://localhost:3000"),
                            allowedMethods = listOf("GET"),
                            allowedHeaders = listOf("*"),
                            allowCredentials = true,
                            maxAge = 3600L,
                        ),
                ),
            )
        ticketController = TicketController(ticketService, jwtService)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAsPatient(ticketId: String) {
        val auth = UsernamePasswordAuthenticationToken(ticketId, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    // --- createTicket ---

    @Test
    fun `createTicket returns 200 with the ticket number`() {
        val request = TicketCreateRequest(type = TicketType.CONSULTATION)
        whenever(ticketService.createTicket(request)).thenReturn(TicketCreateResponse(ticketNumber = "C-001", token = ""))

        val response = ticketController.createTicket(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.ticketNumber).isEqualTo("C-001")
    }

    @Test
    fun `createTicket returns a valid JWT that encodes the ticket number`() {
        val request = TicketCreateRequest(type = TicketType.URGENT)
        whenever(ticketService.createTicket(request)).thenReturn(TicketCreateResponse(ticketNumber = "U-042", token = ""))

        val response = ticketController.createTicket(request)

        val token = response.body?.token!!
        assertThat(jwtService.isTokenValid(token)).isTrue()
        assertThat(jwtService.getTicketIdFromToken(token)).isEqualTo("U-042")
    }

    @Test
    fun `createTicket returns 400 when the service throws`() {
        val request = TicketCreateRequest(type = TicketType.CHECKUP)
        whenever(ticketService.createTicket(request)).thenThrow(RuntimeException("DB error"))

        val response = ticketController.createTicket(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    // --- getTicketStatus ---

    @Test
    fun `getTicketStatus returns 200 with queue position for the patient's own ticket`() {
        authenticateAsPatient("C-001")
        val status = queueStatusResponse("C-001", position = 2, size = 5)
        whenever(ticketService.getQueueStatus("C-001")).thenReturn(status)

        val response = ticketController.getTicketStatus("C-001")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.ticketNumber).isEqualTo("C-001")
        assertThat(response.body?.positionInQueue).isEqualTo(2)
        assertThat(response.body?.queueSize).isEqualTo(5)
    }

    @Test
    fun `getTicketStatus returns 403 when the patient requests another patient's ticket`() {
        authenticateAsPatient("C-002")

        val response = ticketController.getTicketStatus("C-001")

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `getTicketStatus returns 404 when the ticket does not exist`() {
        authenticateAsPatient("C-999")
        whenever(ticketService.getQueueStatus("C-999")).thenReturn(null)

        val response = ticketController.getTicketStatus("C-999")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- cancelTicket ---

    @Test
    fun `cancelTicket returns 200 with cancelled ticket details`() {
        authenticateAsPatient("C-001")
        val details = ticketDetails("C-001", TicketStatus.CANCELLED)
        whenever(ticketService.cancelTicket("C-001")).thenReturn(details)

        val response = ticketController.cancelTicket("C-001")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(TicketStatus.CANCELLED)
        assertThat(response.body?.ticketName).isEqualTo("C-001")
    }

    @Test
    fun `cancelTicket returns 403 when the patient tries to cancel another patient's ticket`() {
        authenticateAsPatient("C-002")

        val response = ticketController.cancelTicket("C-001")

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `cancelTicket returns 404 when the ticket does not exist`() {
        authenticateAsPatient("C-999")
        whenever(ticketService.cancelTicket("C-999")).thenThrow(NoSuchElementException("Ticket not found"))

        val response = ticketController.cancelTicket("C-999")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `cancelTicket returns 409 when the ticket is not in waiting state`() {
        authenticateAsPatient("C-001")
        whenever(ticketService.cancelTicket("C-001")).thenThrow(IllegalStateException("Only waiting tickets can be cancelled"))

        val response = ticketController.cancelTicket("C-001")

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun queueStatusResponse(
        ticketNumber: String,
        position: Int = 1,
        size: Int = 1,
    ) = QueueStatusResponse(
        ticketNumber = ticketNumber,
        status = TicketStatus.WAITING,
        type = TicketType.CONSULTATION,
        positionInQueue = position,
        queueSize = size,
        roomId = null,
        calledAt = null,
    )

    private fun ticketDetails(
        name: String,
        status: TicketStatus,
    ) = TicketDetails(
        id = 1L,
        ticketName = name,
        status = status,
        createdAt = Date(),
        calledAt = null,
        roomId = null,
        doctorId = null,
        type = TicketType.CONSULTATION,
    )
}
