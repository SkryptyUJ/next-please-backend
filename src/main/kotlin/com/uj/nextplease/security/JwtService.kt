package com.uj.nextplease.security

import com.uj.nextplease.user.model.UserDetails
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
        private const val PATIENT_ROLE = "patient"
    }

    fun generateStaffToken(userDetails: UserDetails): String {
        val now = System.currentTimeMillis()
        return Jwts
            .builder()
            .subject(userDetails.email)
            .claim(ROLE_CLAIM, userDetails.role)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(now + securityProperties.staffExpirationMs))
            .signWith(signKey(securityProperties.secretKey))
            .compact()
    }

    fun generatePatientToken(tiketId: String): String {
        val now = System.currentTimeMillis()
        return Jwts
            .builder()
            .subject(GUEST_TOKEN_PREFIX + tiketId)
            .claim(ROLE_CLAIM, PATIENT_ROLE)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(now + securityProperties.patientExpirationMs))
            .signWith(signKey(securityProperties.secretKey))
            .compact()
    }

    fun getUsernameFromToken(token: String): String? = extractAllClaims(token).subject

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
