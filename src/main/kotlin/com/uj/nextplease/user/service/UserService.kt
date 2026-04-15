package com.uj.nextplease.user.service

import com.uj.nextplease.user.User
import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun findByEmail(email: String): UserDetails? {
        val user = userRepository.findByEmail(email)
        return toUserDetails(user ?: return null)
    }

    fun isPasswordCorrect(
        password: String,
        encodedPassword: String,
    ): Boolean = passwordEncoder.matches(password, encodedPassword)

    private fun toUserDetails(user: User): UserDetails =
        UserDetails(
            id = user.id!!,
            email = user.email,
            password = user.password,
            role = user.role,
            name = user.name,
            surname = user.surname,
        )
}
