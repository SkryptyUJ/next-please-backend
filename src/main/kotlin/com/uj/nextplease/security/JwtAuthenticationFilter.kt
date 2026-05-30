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
        val jwt =
            request
                .getHeader("Authorization")
                ?.takeIf { it.startsWith(BEARER_PREFIX) }
                ?.substringAfter(BEARER_PREFIX)
                ?: return filterChain.doFilter(request, response)

        if (jwtService.isTokenValid(jwt)) {
            jwtService.getRoleFromToken(jwt)?.let { tokenRole -> addSecurityContext(tokenRole, jwt) }
        }

        filterChain.doFilter(request, response)
    }

    private fun addSecurityContext(
        tokenRole: String,
        jwt: String,
    ) {
        when (UserRole.valueOf(tokenRole)) {
            UserRole.PATIENT -> handlePatientToken(jwt)
            else -> handleRoleToken(tokenRole, jwt)
        }
    }

    private fun handleRoleToken(
        tokenRole: String,
        jwt: String,
    ) {
        val userRole = UserRole.valueOf(tokenRole)
        val userEmail = jwtService.getEmailFromToken(jwt) ?: return
        val userDetails = userService.findByEmail(userEmail) ?: return

        val authorities = listOf(SimpleGrantedAuthority(ROLE_PREFIX + userRole.name))
        val authenticationToken = UsernamePasswordAuthenticationToken(userEmail, userDetails.password, authorities)

        SecurityContextHolder.getContext().authentication = authenticationToken
    }

    private fun handlePatientToken(jwt: String) {
        val ticketId = jwtService.getTicketIdFromToken(jwt) ?: return

        val authorities = listOf(SimpleGrantedAuthority(ROLE_PREFIX + UserRole.PATIENT.name))
        val authenticationToken = UsernamePasswordAuthenticationToken(ticketId, null, authorities)

        SecurityContextHolder.getContext().authentication = authenticationToken
    }
}
