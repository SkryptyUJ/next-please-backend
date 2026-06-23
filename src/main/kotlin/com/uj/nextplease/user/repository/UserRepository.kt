package com.uj.nextplease.user.repository

import com.uj.nextplease.user.User
import com.uj.nextplease.user.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun findByRole(role: String): List<User>

    fun findByRoleAndStatus(
        role: String,
        status: UserStatus,
    ): List<User>

    fun countByRoleAndStatus(
        role: String,
        status: UserStatus,
    ): Long
}
