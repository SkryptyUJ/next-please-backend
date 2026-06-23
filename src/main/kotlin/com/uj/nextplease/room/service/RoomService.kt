package com.uj.nextplease.room.service

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val ticketRepository: TicketRepository,
    private val ticketService: TicketService,
    private val userRepository: UserRepository,
) {
    fun getAvailableRooms(): List<RoomResponse> = roomRepository.findByDoctorIdIsNull().map(::toRoomResponse)

    fun getRoomById(roomId: Long): RoomResponse? = roomRepository.findById(roomId).orElse(null)?.let(::toRoomResponse)

    fun claimRoom(
        roomId: Long,
        doctorId: Long,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        if (room.doctorId != null) {
            throw IllegalStateException("Room is already taken")
        }
        if (roomRepository.findByDoctorId(doctorId) != null) {
            throw IllegalStateException("Doctor is already seated in another room")
        }

        room.doctorId = doctorId
        room.isActive = true
        return toRoomResponse(roomRepository.save(room))
    }

    fun releaseRoom(
        roomId: Long,
        doctorId: Long,
    ): RoomResponse? {
        val room = roomRepository.findById(roomId).orElse(null) ?: return null

        if (room.doctorId != doctorId) {
            throw AccessDeniedException("Room is not assigned to this doctor")
        }

        ticketRepository
            .findByRoomIdAndStatus(roomId, TicketStatus.CALLED)
            .forEach { ticketService.completeTicket(it.id!!) }

        room.doctorId = null
        room.isActive = false
        return toRoomResponse(roomRepository.save(room))
    }

    private fun toRoomResponse(room: Room): RoomResponse {
        val doctor = room.doctorId?.let { userRepository.findById(it).orElse(null) }

        return RoomResponse(
            id = room.id!!,
            name = room.name,
            isActive = room.isActive,
            doctorId = room.doctorId,
            doctorName = doctor?.name,
            doctorSurname = doctor?.surname,
        )
    }
}
