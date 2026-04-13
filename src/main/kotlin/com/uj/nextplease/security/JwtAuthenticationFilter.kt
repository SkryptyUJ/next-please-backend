package com.uj.nextplease.security

import com.uj.nextplease.user.model.UserRole
import com.uj.nextplease.user.service.UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userService: UserService,
) : OncePerRequestFilter() {
    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader?.startsWith(BEARER_PREFIX) == false) {
            return filterChain.doFilter(request, response)
        }

        val jwt = authHeader.substringAfter(BEARER_PREFIX)

        if (jwtService.isTokenValid(jwt)) {
            val tokenRole = jwtService.getRoleFromToken(jwt)

            when (tokenRole) {
                UserRole.PATIENT.name -> handlePatientToken(jwt)
                UserRole.ADMIN.name -> handleAdminToken(jwt)
                UserRole.DOCTOR.name -> handleDoctorToken(jwt)
            }
        }
    }

    private fun handlePatientToken(jwt: String) {}

    private fun handleDoctorToken(jwt: String) {}

    private fun handleAdminToken(jwt: String) {}
}
