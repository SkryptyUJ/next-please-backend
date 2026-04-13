package com.uj.nextplease.user.repository

import com.uj.nextplease.user.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
}
