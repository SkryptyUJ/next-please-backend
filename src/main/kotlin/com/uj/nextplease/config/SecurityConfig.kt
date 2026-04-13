package com.uj.nextplease.config

import com.uj.nextplease.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
)
