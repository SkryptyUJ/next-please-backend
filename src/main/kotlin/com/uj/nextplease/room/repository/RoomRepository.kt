package com.uj.nextplease.room.repository

import com.uj.nextplease.room.Room
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RoomRepository : JpaRepository<Room, Long> {
    fun findByName(name: String): Room?

    fun findByDoctorId(doctorId: Long): Room?

    @Query("SELECT r FROM Room r WHERE r.isActive = true ORDER BY r.name ASC")
    fun findAllActive(): List<Room>

    fun findAllByOrderByNameAsc(): List<Room>
}
