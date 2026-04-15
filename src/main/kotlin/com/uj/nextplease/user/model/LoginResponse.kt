package com.uj.nextplease.user.model

data class LoginResponse(
    val token: String,
    val email: String,
    val name: String,
    val surname: String,
    val role: String,
)
