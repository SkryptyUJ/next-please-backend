package com.uj.nextplease.user.model

data class RegisterDoctorRequest(
    val email: String,
    val name: String,
    val surname: String,
    val password: String,
)
