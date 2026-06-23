package com.uj.nextplease.security

import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.model.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JwtServiceTest {
    private lateinit var jwtService: JwtService
    private lateinit var securityProperties: SecurityProperties

    private val corsProperties =
        CorsProperties(
            allowedOrigins = listOf("http://localhost:3000"),
            allowedMethods = listOf("GET"),
            allowedHeaders = listOf("*"),
            allowCredentials = true,
            maxAge = 3600L,
        )

    @BeforeEach
    fun setUp() {
        securityProperties =
            SecurityProperties(
                secretKey = "my-secret-key-that-is-at-least-32-characters-long-for-hmac-sha",
                staffExpirationMs = 3600000,
                patientExpirationMs = 1800000,
                cors = corsProperties,
            )
        jwtService = JwtService(securityProperties)
    }

    @Test
    fun `generateStaffToken creates valid token with user email`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "hashedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )

        val token = jwtService.generateStaffToken(userDetails)

        assertThat(token).isNotBlank()
        assertThat(jwtService.isTokenValid(token)).isTrue()
    }

    @Test
    fun `generateStaffToken token contains email as subject`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "hashedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )

        val token = jwtService.generateStaffToken(userDetails)
        val emailFromToken = jwtService.getEmailFromToken(token)

        assertThat(emailFromToken).isEqualTo("doctor@example.com")
    }

    @Test
    fun `generateStaffToken token contains role claim`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "hashedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )

        val token = jwtService.generateStaffToken(userDetails)
        val roleFromToken = jwtService.getRoleFromToken(token)

        assertThat(roleFromToken).isEqualTo("ROLE_DOCTOR")
    }

    @Test
    fun `generatePatientToken creates valid token with ticket ID`() {
        val token = jwtService.generatePatientToken("T-001")

        assertThat(token).isNotBlank()
        assertThat(jwtService.isTokenValid(token)).isTrue()
    }

    @Test
    fun `generatePatientToken token contains ticket ID`() {
        val token = jwtService.generatePatientToken("T-001")
        val ticketIdFromToken = jwtService.getTicketIdFromToken(token)

        assertThat(ticketIdFromToken).isEqualTo("T-001")
    }

    @Test
    fun `generatePatientToken token contains patient role`() {
        val token = jwtService.generatePatientToken("T-001")
        val roleFromToken = jwtService.getRoleFromToken(token)

        assertThat(roleFromToken).isEqualTo(UserRole.PATIENT.name)
    }

    @Test
    fun `isTokenValid returns true for valid token`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "hashedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )
        val token = jwtService.generateStaffToken(userDetails)

        val isValid = jwtService.isTokenValid(token)

        assertThat(isValid).isTrue()
    }

    @Test
    fun `isTokenValid returns false for invalid token`() {
        val invalidToken = "invalid.token.here"

        val isValid = jwtService.isTokenValid(invalidToken)

        assertThat(isValid).isFalse()
    }

    @Test
    fun `isTokenValid returns false for tampered token`() {
        val userDetails =
            UserDetails(
                id = 1L,
                email = "doctor@example.com",
                password = "hashedPassword",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )
        val token = jwtService.generateStaffToken(userDetails)
        val tamperedToken = token.dropLast(5) + "xxxxx"

        val isValid = jwtService.isTokenValid(tamperedToken)

        assertThat(isValid).isFalse()
    }

    @Test
    fun `staff tokens with different roles contain correct role claim`() {
        val roles = listOf("ROLE_DOCTOR", "ROLE_PATIENT")

        roles.forEach { role ->
            val userDetails =
                UserDetails(
                    id = 1L,
                    email = "user@example.com",
                    password = "hashedPassword",
                    role = role,
                    name = "John",
                    surname = "Doe",
                )
            val token = jwtService.generateStaffToken(userDetails)
            val roleFromToken = jwtService.getRoleFromToken(token)

            assertThat(roleFromToken).isEqualTo(role)
        }
    }
}
