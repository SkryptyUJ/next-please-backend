package com.uj.nextplease.user.model

data class UserDetails(
    val id: Long,
    val email: String = "",
    val password: String = "",
    val role: String = "",
    val name: String = "",
    val surname: String = "",
)
