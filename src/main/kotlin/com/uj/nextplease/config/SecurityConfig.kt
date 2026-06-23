package com.uj.nextplease.config

import com.uj.nextplease.security.JwtAuthenticationFilter
import com.uj.nextplease.security.SecurityProperties
import com.uj.nextplease.util.Constants
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Lazy private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val securityProperties: SecurityProperties,
) {
    @Bean
    @ConditionalOnMissingBean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/tickets/create")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/register-doctor")
                    .permitAll()
                    .requestMatchers("/api/auth/token/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole(Constants.ROLE_ADMIN)
                    .requestMatchers(HttpMethod.GET, "/api/tickets/status/**")
                    .hasRole(Constants.ROLE_PATIENT)
                    .requestMatchers(HttpMethod.POST, "/api/tickets/*/cancel")
                    .hasRole(Constants.ROLE_PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/queue/subscribe")
                    .hasRole(Constants.ROLE_PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/rooms/available")
                    .hasRole(Constants.ROLE_DOCTOR)
                    .requestMatchers(HttpMethod.POST, "/api/rooms/*/claim", "/api/rooms/*/release")
                    .hasRole(Constants.ROLE_DOCTOR)
                    .requestMatchers("/api/doctors/**")
                    .hasRole(Constants.ROLE_DOCTOR)
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = securityProperties.cors.allowedOrigins
                allowedMethods = securityProperties.cors.allowedMethods
                allowedHeaders = securityProperties.cors.allowedHeaders
                allowCredentials = securityProperties.cors.allowCredentials
                maxAge = securityProperties.cors.maxAge
            }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
