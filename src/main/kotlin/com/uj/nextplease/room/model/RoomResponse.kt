package com.uj.nextplease.room.model

data class RoomResponse(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val doctorId: Long? = null,
    val doctorName: String? = null,
    val doctorSurname: String? = null,
)
