package com.uj.nextplease.room.service

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.DoctorAssignmentRequest
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.model.RoomUpdateRequest
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val ticketRepository: TicketRepository,
    private val userRepository: UserRepository,
) {
    fun getAllRooms(): List<RoomResponse> = roomRepository.findAllByOrderByNameAsc().map(::toRoomResponse)

    fun getActiveRooms(): List<RoomResponse> = roomRepository.findAllActive().map(::toRoomResponse)

    fun getRoomById(roomId: Long): RoomResponse? = roomRepository.findById(roomId).orElse(null)?.let(::toRoomResponse)

    fun updateRoom(
        roomId: Long,
        request: RoomUpdateRequest,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        request.name?.let { name ->
            val existingRoom = roomRepository.findByName(name)
            if (existingRoom != null && existingRoom.id != roomId) {
                throw IllegalArgumentException("Room name already exists")
            }
            room.name = name
        }

        request.isActive?.let { room.isActive = it }

        val updated = roomRepository.save(room)
        return toRoomResponse(updated)
    }

    fun assignDoctorToRoom(
        roomId: Long,
        request: DoctorAssignmentRequest,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        request.doctorId?.let { doctorId ->
            userRepository.findById(doctorId).orElse(null)
                ?: throw IllegalArgumentException("Doctor not found")

            val existingAssignment = roomRepository.findByDoctorId(doctorId)
            if (existingAssignment != null && existingAssignment.id != roomId) {
                throw IllegalArgumentException("Doctor is already assigned to another room")
            }
        }

        room.doctorId?.takeIf { it != request.doctorId }?.let { currentDoctorId ->
            roomRepository.findByDoctorId(currentDoctorId)?.let { currentDoctorRoom ->
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
        roomRepository.findById(roomId).orElse(null) ?: return false
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
