package com.uj.nextplease.room.controller

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.room.service.RoomService
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RoomController(
    private val roomService: RoomService,
    private val ticketService: TicketService,
    private val roomRepository: RoomRepository,
    private val userRepository: UserRepository,
) {
    @GetMapping("/rooms/available")
    fun getAvailableRooms(): ResponseEntity<List<RoomResponse>> = ResponseEntity.ok(roomService.getAvailableRooms())

    @PostMapping("/rooms/{roomId}/claim")
    fun claimRoom(
        @PathVariable roomId: Long,
    ): ResponseEntity<RoomResponse> {
        val doctorId =
            getAuthenticatedDoctorId()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return try {
            val claimed =
                roomService.claimRoom(roomId, doctorId)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(claimed)
        } catch (_: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @PostMapping("/rooms/{roomId}/release")
    fun releaseRoom(
        @PathVariable roomId: Long,
    ): ResponseEntity<RoomResponse> {
        val doctorId =
            getAuthenticatedDoctorId()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return try {
            val released =
                roomService.releaseRoom(roomId, doctorId)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(released)
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/doctors/room")
    fun getDoctorAssignedRoom(): ResponseEntity<RoomResponse> {
        val room =
            getAuthenticatedDoctorRoom()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val roomResponse =
            roomService.getRoomById(room.id!!)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(roomResponse)
    }

    @GetMapping("/doctors/available-types")
    fun getAvailableTicketTypes(): ResponseEntity<List<TicketType>> = ResponseEntity.ok(ticketService.getAvailableTypes())

    @PostMapping("/doctors/next-patient")
    fun getNextPatient(
        @RequestParam type: TicketType,
    ): ResponseEntity<TicketDetails> {
        val room =
            getAuthenticatedDoctorRoom()
                ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()

        val doctorId =
            room.doctorId
                ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()

        val ticket =
            ticketService.pairNextPatient(type, room.id!!, room.name, doctorId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(ticket)
    }

    @PostMapping("/doctors/complete-patient/{ticketId}")
    fun completePatient(
        @PathVariable ticketId: Long,
    ): ResponseEntity<TicketDetails> {
        val doctorId =
            getAuthenticatedDoctorId()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return try {
            val completed =
                ticketService.completeTicket(ticketId, doctorId)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(completed)
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (_: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    private fun getAuthenticatedDoctorId(): Long? {
        val doctorEmail = SecurityContextHolder.getContext().authentication?.principal as? String ?: return null
        return userRepository.findByEmail(doctorEmail)?.id
    }

    private fun getAuthenticatedDoctorRoom(): Room? = getAuthenticatedDoctorId()?.let(roomRepository::findByDoctorId)
}
