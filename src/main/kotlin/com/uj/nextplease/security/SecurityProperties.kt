package com.uj.nextplease.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security")
data class SecurityProperties(
    val secretKey: String,
    val staffExpirationMs: Long,
    val patientExpirationMs: Long,
)
