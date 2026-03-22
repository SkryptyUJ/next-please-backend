package com.uj.nextplease.room

import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room, Long> {
    fun findByName(name: String): Room?

    fun findByDoctorId(doctorId: Long): Room?
}
