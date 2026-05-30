package com.uj.nextplease.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security")
data class SecurityProperties(
    val secretKey: String,
    val staffExpirationMs: Long,
    val patientExpirationMs: Long,
    val cors: CorsProperties,
)

data class CorsProperties(
    val allowedOrigins: List<String>,
    val allowedMethods: List<String>,
    val allowedHeaders: List<String>,
    val allowCredentials: Boolean,
    val maxAge: Long,
)
