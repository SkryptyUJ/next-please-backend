package com.uj.nextplease.user

import com.uj.nextplease.config.PostgresTestContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class UserRepositoryPersistenceTest(
    @Autowired private val userRepository: UserRepository,
) {
    @Test
    fun `save user and find by email`() {
        val saved =
            userRepository.save(
                User(
                    email = "doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "John",
                    surname = "Doe",
                ),
            )

        val found = userRepository.findByEmail("doctor@example.com")

        assertThat(saved.id).isNotNull()
        assertThat(found).isNotNull()
        assertThat(found?.id).isEqualTo(saved.id)
    }

    @Test
    fun `find by email returns null for unknown value`() {
        assertThat(userRepository.findByEmail("missing@example.com")).isNull()
    }
}
