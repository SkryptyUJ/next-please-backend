package com.uj.nextplease.user.service

import com.uj.nextplease.user.User
import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

class UserServiceTest {
    private val userRepository: UserRepository = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userService = UserService(userRepository, passwordEncoder)
    }

    @Test
    fun `findByEmail returns user details when user exists`() {
        val user =
            User(
                id = 1L,
                email = "doctor@example.com",
                password = "encodedPassword123",
                role = "ROLE_DOCTOR",
                name = "John",
                surname = "Doe",
            )
        whenever(userRepository.findByEmail("doctor@example.com")).thenReturn(user)

        val result = userService.findByEmail("doctor@example.com")

        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(1L)
        assertThat(result?.email).isEqualTo("doctor@example.com")
        assertThat(result?.password).isEqualTo("encodedPassword123")
        assertThat(result?.role).isEqualTo("ROLE_DOCTOR")
        assertThat(result?.name).isEqualTo("John")
        assertThat(result?.surname).isEqualTo("Doe")
    }

    @Test
    fun `findByEmail returns null when user does not exist`() {
        whenever(userRepository.findByEmail("missing@example.com")).thenReturn(null)

        val result = userService.findByEmail("missing@example.com")

        assertThat(result).isNull()
    }

    @Test
    fun `isPasswordCorrect returns true when password matches`() {
        val rawPassword = "myPassword123"
        val encodedPassword = "encodedPassword123"
        whenever(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true)

        val result = userService.isPasswordCorrect(rawPassword, encodedPassword)

        assertThat(result).isTrue()
    }

    @Test
    fun `isPasswordCorrect returns false when password does not match`() {
        val rawPassword = "wrongPassword"
        val encodedPassword = "encodedPassword123"
        whenever(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false)

        val result = userService.isPasswordCorrect(rawPassword, encodedPassword)

        assertThat(result).isFalse()
    }

    @Test
    fun `findByEmail converts user to user details correctly`() {
        val user =
            User(
                id = 42L,
                email = "patient@example.com",
                password = "hashedPass",
                role = "ROLE_PATIENT",
                name = "Jane",
                surname = "Smith",
            )
        whenever(userRepository.findByEmail("patient@example.com")).thenReturn(user)

        val result = userService.findByEmail("patient@example.com")

        assertThat(result).isInstanceOf(UserDetails::class.java)
        assertThat(result?.id).isEqualTo(42L)
        assertThat(result?.email).isEqualTo("patient@example.com")
    }
}
