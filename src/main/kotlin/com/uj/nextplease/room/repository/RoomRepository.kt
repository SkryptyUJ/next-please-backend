package com.uj.nextplease.room.repository

import com.uj.nextplease.room.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room, Long> {
    fun findByDoctorId(doctorId: Long): Room?

    fun findByDoctorIdIsNull(): List<Room>
}
