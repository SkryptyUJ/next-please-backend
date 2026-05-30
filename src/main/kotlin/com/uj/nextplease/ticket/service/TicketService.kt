package com.uj.nextplease.ticket.service

import com.uj.nextplease.queue.service.QueueService
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketCreateResponse
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.util.Constants
import org.springframework.stereotype.Service
import java.util.Date
import kotlin.random.Random

@Service
class TicketService(
    private val ticketRepository: TicketRepository,
    private val roomRepository: RoomRepository,
    private val queueService: QueueService,
) {
    companion object {
        private const val TICKET_NUMBER_LENGTH = Constants.DEFAULT_TICKET_NUMBER_LENGTH
        private val TICKET_RANDOM_MAX = Constants.DEFAULT_TICKET_RANDOM_MAX
    }

    fun findByTicketName(ticketName: String): TicketDetails? = ticketRepository.findByTicketName(ticketName)?.let(::toTicketDetails)

    fun createTicket(request: TicketCreateRequest): TicketCreateResponse {
        val room =
            roomRepository
                .findById(request.roomId)
                .orElseThrow { IllegalArgumentException("Room not found") }

        val ticketNumber = generateTicketNumber(room.name)

        val ticket =
            Ticket(
                ticketName = ticketNumber,
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = request.roomId,
                type = request.type,
            )

        ticketRepository.save(ticket)

        val status = getQueueStatus(ticketNumber)
        if (status != null) {
            queueService.broadcastQueueUpdate(request.roomId, status)
        }

        return TicketCreateResponse(
            ticketNumber = ticketNumber,
            token = "",
            roomId = request.roomId,
        )
    }

    fun getQueueStatus(ticketName: String): QueueStatusResponse? {
        val ticket = ticketRepository.findByTicketName(ticketName) ?: return null
        val ticketDetails = toTicketDetails(ticket)

        val queueSize = ticket.roomId?.let(ticketRepository::countWaitingByRoomId) ?: 0

        val position =
            ticket.roomId
                ?.takeIf { ticket.status == TicketStatus.WAITING }
                ?.let { roomId ->
                    ticketRepository
                        .findWaitingByRoomIdOrderedByCreatedAt(roomId)
                        .indexOfFirst { it.id == ticket.id } + 1
                }
                ?: 0

        return QueueStatusResponse(
            ticketNumber = ticketDetails.ticketName,
            status = ticketDetails.status,
            type = ticketDetails.type,
            positionInQueue = position,
            queueSize = queueSize,
            roomId = ticket.roomId,
        )
    }

    fun getNextPatientByType(
        roomId: Long,
        type: TicketType,
    ): TicketDetails? =
        ticketRepository
            .findWaitingByRoomIdAndTypeOrderedByCreatedAt(roomId, type)
            .firstOrNull()
            ?.let(::toTicketDetails)

    fun callPatient(
        ticketId: Long,
        roomId: Long,
    ): TicketDetails? {
        val ticket =
            ticketRepository
                .findById(ticketId)
                .orElse(null) ?: return null

        if (ticket.roomId != roomId) {
            throw IllegalArgumentException("Ticket does not belong to this room")
        }

        ticket.status = TicketStatus.CALLED
        ticket.calledAt = Date()

        val updated = ticketRepository.save(ticket)

        roomRepository
            .findById(roomId)
            .orElse(null)
            ?.let { room -> queueService.broadcastPatientCalled(roomId, ticket.ticketName!!, room.name) }

        broadcastQueueUpdateForRoom(roomId)

        return toTicketDetails(updated)
    }

    fun completeTicket(ticketId: Long): TicketDetails? {
        val ticket =
            ticketRepository
                .findById(ticketId)
                .orElse(null) ?: return null

        ticket.status = TicketStatus.COMPLETED
        val updated = ticketRepository.save(ticket)

        ticket.roomId?.let { broadcastQueueUpdateForRoom(it) }

        return toTicketDetails(updated)
    }

    fun cancelTicket(ticketId: Long): TicketDetails? {
        val ticket =
            ticketRepository
                .findById(ticketId)
                .orElse(null) ?: return null

        ticket.status = TicketStatus.CANCELLED
        val updated = ticketRepository.save(ticket)

        ticket.roomId?.let { broadcastQueueUpdateForRoom(it) }

        return toTicketDetails(updated)
    }

    fun getAvailableTypes(roomId: Long): List<TicketType> {
        val waitingTickets = ticketRepository.findWaitingByRoomIdOrderedByCreatedAt(roomId)
        return waitingTickets.map { it.type }.distinct()
    }

    private fun generateTicketNumber(roomName: String): String {
        val roomPrefix = roomName.take(1).uppercase()
        val randomPart = Random.nextInt(TICKET_RANDOM_MAX).toString().padStart(TICKET_NUMBER_LENGTH, '0')
        return "$roomPrefix-$randomPart"
    }

    private fun toTicketDetails(ticket: Ticket): TicketDetails =
        TicketDetails(
            id = ticket.id!!,
            ticketName = ticket.ticketName!!,
            status = ticket.status!!,
            createdAt = ticket.createdAt!!,
            calledAt = ticket.calledAt,
            roomId = ticket.roomId,
            doctorId = ticket.doctorId,
            type = ticket.type,
        )

    private fun broadcastQueueUpdateForRoom(roomId: Long) {
        val remainingQueue = ticketRepository.findWaitingByRoomIdOrderedByCreatedAt(roomId)
        remainingQueue.forEach { remainingTicket ->
            val status = getQueueStatus(remainingTicket.ticketName!!)
            if (status != null) {
                queueService.broadcastQueueUpdate(roomId, status)
            }
        }
    }
}
