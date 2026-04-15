package com.uj.nextplease.user.controller

import com.uj.nextplease.security.JwtService
import com.uj.nextplease.security.SecurityProperties
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.model.LoginRequest
import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class AuthControllerTest {
    private lateinit var userService: UserService
    private lateinit var ticketService: TicketService
    private lateinit var jwtService: JwtService
    private lateinit var authController: AuthController
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        userService = mock()
        ticketService = mock()
        securityProperties =
            SecurityProperties(
                secretKey = "my-secret-key-that-is-at-least-32-characters-long-for-hmac-sha",
                staffExpirationMs = 3600000,
                patientExpirationMs = 1800000,
            )
        jwtService = JwtService(securityProperties)
        authController = AuthController(userService, ticketService, jwtService)
    }

    @Test
    fun `login returns 401 when user not found`() {
        val loginRequest = LoginRequest(email = "unknown@example.com", password = "password")
        whenever(userService.findByEmail("unknown@example.com")).thenReturn(null)

        val response = authController.login(loginRequest)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body).isNull()
    }

    @Test
    fun `login returns 401 when password is incorrect`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "encodedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )
        val loginRequest = LoginRequest(email = "doctor@example.com", password = "wrongPassword")
        whenever(userService.findByEmail("doctor@example.com")).thenReturn(userDetails)
        whenever(userService.isPasswordCorrect("wrongPassword", "encodedPassword")).thenReturn(false)

        val response = authController.login(loginRequest)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login returns 200 with token when credentials are correct`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "encodedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )
        val loginRequest = LoginRequest(email = "doctor@example.com", password = "password123")
        whenever(userService.findByEmail("doctor@example.com")).thenReturn(userDetails)
        whenever(userService.isPasswordCorrect("password123", "encodedPassword")).thenReturn(true)

        val response = authController.login(loginRequest)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull()
        assertThat(response.body?.token).isNotBlank()
        assertThat(response.body?.email).isEqualTo("doctor@example.com")
        assertThat(response.body?.name).isEqualTo("John")
        assertThat(response.body?.surname).isEqualTo("Doe")
        assertThat(response.body?.role).isEqualTo("ROLE_DOCTOR")
    }

    @Test
    fun `generatePatientToken contains ticket ID in response`() {
        val ticketId = "T-123"
        whenever(ticketService.findByTicketName(ticketId)).thenReturn(mock())

        val response = authController.generatePatientToken(ticketId)

        assertThat(response.body?.ticketId).isEqualTo(ticketId)
    }

    @Test
    fun `generatePatientToken token is valid`() {
        val ticketId = "T-001"
        whenever(ticketService.findByTicketName(ticketId)).thenReturn(mock())

        val response = authController.generatePatientToken(ticketId)

        assertThat(jwtService.isTokenValid(response.body?.token!!)).isTrue()
    }

    @Test
    fun `generatePatientToken token contains correct ticket ID`() {
        val ticketId = "T-001"
        whenever(ticketService.findByTicketName(ticketId)).thenReturn(mock())

        val response = authController.generatePatientToken(ticketId)

        val extractedTicketId = jwtService.getTicketIdFromToken(response.body?.token!!)
        assertThat(extractedTicketId).isEqualTo(ticketId)
    }

    @Test
    fun `login works with admin role`() {
        val userDetails =
            UserDetails(
                id = 2L,
                email = "admin@example.com",
                password = "encodedPassword",
                role = "ROLE_ADMIN",
                name = "Admin",
                surname = "User",
            )
        val loginRequest = LoginRequest(email = "admin@example.com", password = "password123")
        whenever(userService.findByEmail("admin@example.com")).thenReturn(userDetails)
        whenever(userService.isPasswordCorrect("password123", "encodedPassword")).thenReturn(true)

        val response = authController.login(loginRequest)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.role).isEqualTo("ROLE_ADMIN")
    }
}
