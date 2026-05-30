package com.uj.nextplease.room.service

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.DoctorAssignmentRequest
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.model.RoomUpdateRequest
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val ticketRepository: TicketRepository,
    private val userRepository: UserRepository,
) {
    fun getAllRooms(): List<RoomResponse> {
        val rooms = roomRepository.findAllByOrderByNameAsc()
        return rooms.map { toRoomResponse(it) }
    }

    fun getActiveRooms(): List<RoomResponse> {
        val rooms = roomRepository.findAllActive()
        return rooms.map { toRoomResponse(it) }
    }

    fun getRoomById(roomId: Long): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null
        return toRoomResponse(room)
    }

    fun updateRoom(
        roomId: Long,
        request: RoomUpdateRequest,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        if (request.name != null) {
            val existingRoom = roomRepository.findByName(request.name)
            if (existingRoom != null && existingRoom.id != roomId) {
                throw IllegalArgumentException("Room name already exists")
            }
            room.name = request.name
        }

        if (request.isActive != null) {
            room.isActive = request.isActive
        }

        val updated = roomRepository.save(room)
        return toRoomResponse(updated)
    }

    fun assignDoctorToRoom(
        roomId: Long,
        request: DoctorAssignmentRequest,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        if (request.doctorId != null) {
            val doctor =
                userRepository.findById(request.doctorId).orElse(null)
                    ?: throw IllegalArgumentException("Doctor not found")

            val existingAssignment = roomRepository.findByDoctorId(request.doctorId)
            if (existingAssignment != null && existingAssignment.id != roomId) {
                throw IllegalArgumentException("Doctor is already assigned to another room")
            }
        }

        if (room.doctorId != null && request.doctorId != room.doctorId) {
            val currentDoctorRoom = roomRepository.findByDoctorId(room.doctorId!!)
            if (currentDoctorRoom != null) {
                currentDoctorRoom.doctorId = null
                roomRepository.save(currentDoctorRoom)
            }
        }

        room.doctorId = request.doctorId
        val updated = roomRepository.save(room)
        return toRoomResponse(updated)
    }

    fun createRoom(roomName: String): RoomResponse {
        val existingRoom = roomRepository.findByName(roomName)
        if (existingRoom != null) {
            throw IllegalArgumentException("Room with this name already exists")
        }

        val room =
            Room(
                name = roomName,
                isActive = true,
            )

        val saved = roomRepository.save(room)
        return toRoomResponse(saved)
    }

    fun deleteRoom(roomId: Long): Boolean {
        val room = roomRepository.findById(roomId).orElse(null) ?: return false
        roomRepository.deleteById(roomId)
        return true
    }

    private fun toRoomResponse(room: Room): RoomResponse {
        val waitingCount = ticketRepository.countWaitingByRoomId(room.id!!)
        val doctor = room.doctorId?.let { userRepository.findById(it).orElse(null) }

        return RoomResponse(
            id = room.id!!,
            name = room.name,
            isActive = room.isActive,
            doctorId = room.doctorId,
            doctorName = doctor?.name,
            doctorSurname = doctor?.surname,
            waitingQueueSize = waitingCount,
        )
    }
}
