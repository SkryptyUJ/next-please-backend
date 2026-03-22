package com.uj.nextplease.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration
class PostgresTestContainerConfig {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("nextplease_test")
            .withUsername("nextplease")
            .withPassword("nextplease")
}
