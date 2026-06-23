package com.uj.nextplease.user.controller

import com.uj.nextplease.security.JwtService
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.model.LoginRequest
import com.uj.nextplease.user.model.LoginResponse
import com.uj.nextplease.user.model.PatientTokenResponse
import com.uj.nextplease.user.model.RegisterDoctorRequest
import com.uj.nextplease.user.model.UserStatus
import com.uj.nextplease.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val ticketService: TicketService,
    private val jwtService: JwtService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody loginRequest: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        val userDetails =
            userService.findByEmail(loginRequest.email)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (!userService.isPasswordCorrect(loginRequest.password, userDetails.password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        if (userDetails.status != UserStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val token = jwtService.generateStaffToken(userDetails)

        return ResponseEntity.ok(
            LoginResponse(
                token = token,
                email = userDetails.email,
                name = userDetails.name,
                surname = userDetails.surname,
                role = userDetails.role,
            ),
        )
    }

    @PostMapping("/register-doctor")
    fun registerDoctor(
        @RequestBody request: RegisterDoctorRequest,
    ): ResponseEntity<Map<String, String>> {
        userService.registerDoctor(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapOf("message" to "Request submitted. An admin must approve your account before you can log in."))
    }

    @PostMapping("/token/{ticketId}")
    fun generatePatientToken(
        @PathVariable ticketId: String,
    ): ResponseEntity<PatientTokenResponse> {
        ticketService.findByTicketName(ticketId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val token = jwtService.generatePatientToken(ticketId)

        return ResponseEntity.ok(PatientTokenResponse(token = token, ticketId = ticketId))
    }
}
