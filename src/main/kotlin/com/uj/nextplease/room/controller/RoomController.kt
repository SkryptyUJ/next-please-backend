package com.uj.nextplease.room.controller

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.DoctorAssignmentRequest
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.model.RoomUpdateRequest
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.room.service.RoomService
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
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
    @GetMapping("/rooms")
    fun getAllRooms(): ResponseEntity<List<RoomResponse>> {
        val rooms = roomService.getAllRooms()
        return ResponseEntity.ok(rooms)
    }

    @PutMapping("/rooms/{roomId}")
    fun updateRoom(
        @PathVariable roomId: Long,
        @RequestBody request: RoomUpdateRequest,
    ): ResponseEntity<RoomResponse> {
        return try {
            val updated =
                roomService.updateRoom(roomId, request)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(updated)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    @PostMapping("/rooms/{roomId}/assign-doctor")
    fun assignDoctorToRoom(
        @PathVariable roomId: Long,
        @RequestBody request: DoctorAssignmentRequest,
    ): ResponseEntity<RoomResponse> {
        return try {
            val updated =
                roomService.assignDoctorToRoom(roomId, request)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(updated)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
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
    fun getAvailableTicketTypes(): ResponseEntity<List<TicketType>> {
        val room =
            getAuthenticatedDoctorRoom()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val availableTypes = ticketService.getAvailableTypes(room.id!!)
        return ResponseEntity.ok(availableTypes)
    }

    @PostMapping("/doctors/next-patient")
    fun getNextPatient(
        @RequestParam type: TicketType,
    ): ResponseEntity<TicketDetails> {
        val room =
            getAuthenticatedDoctorRoom()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val nextPatient =
            ticketService.getNextPatientByType(room.id!!, type)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val calledPatient =
            ticketService.callPatient(nextPatient.id, room.id!!)
                ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        return ResponseEntity.ok(calledPatient)
    }

    @PostMapping("/doctors/complete-patient/{ticketId}")
    fun completePatient(
        @PathVariable ticketId: Long,
    ): ResponseEntity<TicketDetails> {
        val completed =
            ticketService.completeTicket(ticketId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(completed)
    }

    @PostMapping("/admin/tickets/create")
    fun createTicketAsAdmin(
        @RequestBody request: TicketCreateRequest,
    ): ResponseEntity<TicketDetails> {
        return try {
            val ticketResponse = ticketService.createTicket(request)
            val ticket =
                ticketService.findByTicketName(ticketResponse.ticketNumber)
                    ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            ResponseEntity.ok(ticket)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    @PostMapping("/admin/tickets/{ticketId}/cancel")
    fun cancelTicket(
        @PathVariable ticketId: Long,
    ): ResponseEntity<TicketDetails> {
        val cancelled =
            ticketService.cancelTicket(ticketId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(cancelled)
    }

    private fun getAuthenticatedDoctorRoom(): Room? {
        val doctorEmail =
            SecurityContextHolder.getContext().authentication?.principal as? String
                ?: return null
        val doctor =
            userRepository.findByEmail(doctorEmail)
                ?: return null
        val doctorId =
            doctor.id
                ?: return null

        return roomRepository.findByDoctorId(doctorId)
    }
}
