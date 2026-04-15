package com.uj.nextplease.security

import com.uj.nextplease.user.model.UserRole
import com.uj.nextplease.user.service.UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userService: UserService,
) : OncePerRequestFilter() {
    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLE_PREFIX = "ROLE_"
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
            val tokenRole = jwtService.getRoleFromToken(jwt) ?: return filterChain.doFilter(request, response)
            addSecurityContext(tokenRole, jwt)
        }

        filterChain.doFilter(request, response)
    }

    private fun addSecurityContext(
        tokenRole: String,
        jwt: String,
    ) {
        val userRole = UserRole.valueOf(tokenRole)
        when (tokenRole) {
            UserRole.PATIENT.name -> handlePatientToken(jwt)
            else -> handleRoleToken(userRole, jwt)
        }
    }

    private fun handleRoleToken(
        userRole: UserRole,
        jwt: String,
    ) {
        val userEmail = jwtService.getEmailFromToken(jwt) ?: return
        val userDetails = userService.findByEmail(userEmail) ?: return

        val authorities = listOf(SimpleGrantedAuthority(ROLE_PREFIX + userRole.name))
        val authenticationToken =
            UsernamePasswordAuthenticationToken(userEmail, userDetails.password, authorities)

        SecurityContextHolder.getContext().authentication = authenticationToken
    }

    private fun handlePatientToken(jwt: String) {
        val ticketId = jwtService.getTicketIdFromToken(jwt) ?: return

        val authorities = listOf(SimpleGrantedAuthority(ROLE_PREFIX + UserRole.PATIENT.name))
        val authenticationToken =
            UsernamePasswordAuthenticationToken(ticketId, null, authorities)

        SecurityContextHolder.getContext().authentication = authenticationToken
    }
}
