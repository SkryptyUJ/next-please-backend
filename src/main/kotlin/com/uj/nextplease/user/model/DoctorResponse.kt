package com.uj.nextplease.user.model

data class DoctorResponse(
    val id: Long,
    val email: String,
    val name: String,
    val surname: String,
    val status: UserStatus,
)
