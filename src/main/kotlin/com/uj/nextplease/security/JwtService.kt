package com.uj.nextplease.security

import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.model.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    private val securityProperties: SecurityProperties,
) {
    companion object {
        private const val ROLE_CLAIM = "role"
        private const val GUEST_TOKEN_PREFIX = "guest-"
    }

    fun generateStaffToken(userDetails: UserDetails): String {
        val now = System.currentTimeMillis()
        return Jwts
            .builder()
            .subject(userDetails.email)
            .claim(ROLE_CLAIM, userDetails.role)
            .issuedAt(Date(now))
            .expiration(Date(now + securityProperties.staffExpirationMs))
            .signWith(signKey(securityProperties.secretKey))
            .compact()
    }

    fun generatePatientToken(ticketId: String): String {
        val now = System.currentTimeMillis()
        return Jwts
            .builder()
            .subject(GUEST_TOKEN_PREFIX + ticketId)
            .claim(ROLE_CLAIM, UserRole.PATIENT.name)
            .issuedAt(Date(now))
            .expiration(Date(now + securityProperties.patientExpirationMs))
            .signWith(signKey(securityProperties.secretKey))
            .compact()
    }

    fun isTokenValid(token: String): Boolean =
        try {
            val claims = extractAllClaims(token)
            claims.expiration.after(Date())
        } catch (_: Exception) {
            false
        }

    fun getRoleFromToken(token: String): String? = extractAllClaims(token)[ROLE_CLAIM] as String?

    fun getEmailFromToken(token: String): String? {
        val subject = getTokenSubject(token)
        return subject.takeUnless { it.startsWith(GUEST_TOKEN_PREFIX) }
    }

    fun getTicketIdFromToken(token: String): String? {
        val subject = getTokenSubject(token)
        return subject
            .takeIf { it.startsWith(GUEST_TOKEN_PREFIX) }
            ?.removePrefix(GUEST_TOKEN_PREFIX)
    }

    private fun getTokenSubject(token: String): String = extractAllClaims(token).subject

    private fun extractAllClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(signKey(securityProperties.secretKey))
            .build()
            .parseSignedClaims(token)
            .payload

    private fun signKey(key: String): SecretKey {
        val keyBytes = key.toByteArray()
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
