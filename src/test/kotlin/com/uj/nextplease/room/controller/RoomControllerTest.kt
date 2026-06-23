package com.uj.nextplease.room.controller

import com.uj.nextplease.room.Room
import com.uj.nextplease.room.model.RoomResponse
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.room.service.RoomService
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.service.TicketService
import com.uj.nextplease.user.User
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Date

class RoomControllerTest {
    private lateinit var roomService: RoomService
    private lateinit var ticketService: TicketService
    private lateinit var roomRepository: RoomRepository
    private lateinit var userRepository: UserRepository
    private lateinit var roomController: RoomController

    private val doctorEmail = "doctor@clinic.com"
    private val doctorId = 42L

    @BeforeEach
    fun setUp() {
        roomService = mock()
        ticketService = mock()
        roomRepository = mock()
        userRepository = mock()
        roomController = RoomController(roomService, ticketService, roomRepository, userRepository)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAsDoctor() {
        val auth = UsernamePasswordAuthenticationToken(doctorEmail, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        whenever(userRepository.findByEmail(doctorEmail)).thenReturn(
            User(id = doctorId, email = doctorEmail, password = "encoded", role = "DOCTOR", name = "Doc", surname = "Tor"),
        )
    }

    // --- getAvailableRooms ---

    @Test
    fun `getAvailableRooms returns 200 with all free rooms`() {
        whenever(roomService.getAvailableRooms()).thenReturn(listOf(roomResponse(1L), roomResponse(2L)))

        val response = roomController.getAvailableRooms()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(2)
    }

    @Test
    fun `getAvailableRooms returns 200 with empty list when all rooms are occupied`() {
        whenever(roomService.getAvailableRooms()).thenReturn(emptyList())

        val response = roomController.getAvailableRooms()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }

    // --- claimRoom ---

    @Test
    fun `claimRoom returns 200 with room when doctor successfully claims it`() {
        authenticateAsDoctor()
        whenever(roomService.claimRoom(10L, doctorId)).thenReturn(roomResponse(10L, doctorId = doctorId))

        val response = roomController.claimRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.doctorId).isEqualTo(doctorId)
    }

    @Test
    fun `claimRoom returns 404 when there is no authenticated principal`() {
        val response = roomController.claimRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `claimRoom returns 404 when the authenticated email is not a known user`() {
        val auth = UsernamePasswordAuthenticationToken(doctorEmail, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        whenever(userRepository.findByEmail(doctorEmail)).thenReturn(null)

        val response = roomController.claimRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `claimRoom returns 404 when the room does not exist`() {
        authenticateAsDoctor()
        whenever(roomService.claimRoom(99L, doctorId)).thenReturn(null)

        val response = roomController.claimRoom(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `claimRoom returns 409 when the room is already occupied`() {
        authenticateAsDoctor()
        whenever(roomService.claimRoom(10L, doctorId)).thenThrow(IllegalStateException("Room is already taken"))

        val response = roomController.claimRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `claimRoom returns 409 when the doctor is already seated in another room`() {
        authenticateAsDoctor()
        whenever(roomService.claimRoom(10L, doctorId)).thenThrow(IllegalStateException("Doctor is already seated in another room"))

        val response = roomController.claimRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    // --- releaseRoom ---

    @Test
    fun `releaseRoom returns 200 with freed room when doctor successfully releases it`() {
        authenticateAsDoctor()
        whenever(roomService.releaseRoom(10L, doctorId)).thenReturn(roomResponse(10L, doctorId = null))

        val response = roomController.releaseRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.doctorId).isNull()
    }

    @Test
    fun `releaseRoom returns 404 when there is no authenticated principal`() {
        val response = roomController.releaseRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `releaseRoom returns 404 when the room does not exist`() {
        authenticateAsDoctor()
        whenever(roomService.releaseRoom(99L, doctorId)).thenReturn(null)

        val response = roomController.releaseRoom(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `releaseRoom returns 403 when doctor does not own the room`() {
        authenticateAsDoctor()
        whenever(roomService.releaseRoom(10L, doctorId)).thenThrow(AccessDeniedException("Room is not assigned to this doctor"))

        val response = roomController.releaseRoom(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    // --- getDoctorAssignedRoom ---

    @Test
    fun `getDoctorAssignedRoom returns 200 with the doctor's current room`() {
        authenticateAsDoctor()
        val room = Room(id = 10L, name = "Room A", isActive = true, doctorId = doctorId)
        whenever(roomRepository.findByDoctorId(doctorId)).thenReturn(room)
        whenever(roomService.getRoomById(10L)).thenReturn(roomResponse(10L, doctorId = doctorId))

        val response = roomController.getDoctorAssignedRoom()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.doctorId).isEqualTo(doctorId)
    }

    @Test
    fun `getDoctorAssignedRoom returns 404 when the doctor has no room`() {
        authenticateAsDoctor()
        whenever(roomRepository.findByDoctorId(doctorId)).thenReturn(null)

        val response = roomController.getDoctorAssignedRoom()

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getDoctorAssignedRoom returns 404 when there is no authenticated principal`() {
        val response = roomController.getDoctorAssignedRoom()

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- getAvailableTicketTypes ---

    @Test
    fun `getAvailableTicketTypes returns 200 with types that have waiting patients`() {
        whenever(ticketService.getAvailableTypes()).thenReturn(listOf(TicketType.CONSULTATION, TicketType.URGENT))

        val response = roomController.getAvailableTicketTypes()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).containsExactly(TicketType.CONSULTATION, TicketType.URGENT)
    }

    @Test
    fun `getAvailableTicketTypes returns 200 with empty list when no patients are waiting`() {
        whenever(ticketService.getAvailableTypes()).thenReturn(emptyList())

        val response = roomController.getAvailableTicketTypes()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }

    // --- getNextPatient ---

    @Test
    fun `getNextPatient returns 200 with the called ticket`() {
        authenticateAsDoctor()
        val room = Room(id = 10L, name = "Room A", isActive = true, doctorId = doctorId)
        whenever(roomRepository.findByDoctorId(doctorId)).thenReturn(room)
        val ticket = ticketDetails("C-001", TicketStatus.CALLED, TicketType.CONSULTATION)
        whenever(ticketService.pairNextPatient(TicketType.CONSULTATION, 10L, "Room A", doctorId)).thenReturn(ticket)

        val response = roomController.getNextPatient(TicketType.CONSULTATION)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(TicketStatus.CALLED)
        assertThat(response.body?.ticketName).isEqualTo("C-001")
    }

    @Test
    fun `getNextPatient returns 409 when the doctor has no room assigned`() {
        authenticateAsDoctor()
        whenever(roomRepository.findByDoctorId(doctorId)).thenReturn(null)

        val response = roomController.getNextPatient(TicketType.CONSULTATION)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `getNextPatient returns 404 when no patients of that type are waiting`() {
        authenticateAsDoctor()
        val room = Room(id = 10L, name = "Room A", isActive = true, doctorId = doctorId)
        whenever(roomRepository.findByDoctorId(doctorId)).thenReturn(room)
        whenever(ticketService.pairNextPatient(TicketType.URGENT, 10L, "Room A", doctorId)).thenReturn(null)

        val response = roomController.getNextPatient(TicketType.URGENT)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- completePatient ---

    @Test
    fun `completePatient returns 200 with the completed ticket`() {
        authenticateAsDoctor()
        val ticket = ticketDetails("C-001", TicketStatus.COMPLETED, TicketType.CONSULTATION)
        whenever(ticketService.completeTicket(1L, doctorId)).thenReturn(ticket)

        val response = roomController.completePatient(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(TicketStatus.COMPLETED)
    }

    @Test
    fun `completePatient returns 404 when there is no authenticated principal`() {
        val response = roomController.completePatient(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `completePatient returns 404 when the ticket does not exist`() {
        authenticateAsDoctor()
        whenever(ticketService.completeTicket(99L, doctorId)).thenReturn(null)

        val response = roomController.completePatient(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `completePatient returns 403 when the doctor does not own the ticket`() {
        authenticateAsDoctor()
        whenever(ticketService.completeTicket(1L, doctorId)).thenThrow(AccessDeniedException("Ticket is not assigned to this doctor"))

        val response = roomController.completePatient(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `completePatient returns 409 when the ticket is not in called state`() {
        authenticateAsDoctor()
        whenever(ticketService.completeTicket(1L, doctorId)).thenThrow(IllegalStateException("Only called tickets can be completed"))

        val response = roomController.completePatient(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun roomResponse(
        id: Long,
        doctorId: Long? = null,
    ) = RoomResponse(
        id = id,
        name = "Room $id",
        isActive = doctorId != null,
        doctorId = doctorId,
    )

    private fun ticketDetails(
        name: String,
        status: TicketStatus,
        type: TicketType,
    ) = TicketDetails(
        id = 1L,
        ticketName = name,
        status = status,
        createdAt = Date(),
        calledAt = if (status == TicketStatus.CALLED || status == TicketStatus.COMPLETED) Date() else null,
        roomId = 10L,
        doctorId = doctorId,
        type = type,
    )
}
