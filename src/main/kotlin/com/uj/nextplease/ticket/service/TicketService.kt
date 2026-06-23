package com.uj.nextplease.ticket.service

import com.uj.nextplease.queue.service.QueueService
import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.ticket.model.TicketCreateRequest
import com.uj.nextplease.ticket.model.TicketCreateResponse
import com.uj.nextplease.ticket.model.TicketDetails
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import com.uj.nextplease.ticket.model.VisitResponse
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.util.Constants
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import kotlin.random.Random

@Service
class TicketService(
    private val ticketRepository: TicketRepository,
    private val queueService: QueueService,
) {
    companion object {
        private const val TICKET_NUMBER_LENGTH = Constants.DEFAULT_TICKET_NUMBER_LENGTH
        private val TICKET_RANDOM_MAX = Constants.DEFAULT_TICKET_RANDOM_MAX
        private val VISIT_DURATION_MS = Constants.VISIT_DURATION_SECONDS * 1000L
    }

    fun findByTicketName(ticketName: String): TicketDetails? = ticketRepository.findByTicketName(ticketName)?.let(::toTicketDetails)

    fun createTicket(request: TicketCreateRequest): TicketCreateResponse {
        val ticketNumber = generateTicketNumber(request.type)

        val ticket =
            Ticket(
                ticketName = ticketNumber,
                status = TicketStatus.WAITING,
                createdAt = Date(),
                roomId = null,
                doctorId = null,
                type = request.type,
            )

        ticketRepository.save(ticket)

        broadcastQueueUpdateForType(request.type)

        return TicketCreateResponse(ticketNumber = ticketNumber, token = "")
    }

    fun getQueueStatus(ticketName: String): QueueStatusResponse? {
        val ticket = ticketRepository.findByTicketName(ticketName) ?: return null
        val ticketDetails = toTicketDetails(ticket)

        val queueSize = ticketRepository.countWaitingByType(ticket.type)

        val position =
            if (ticket.status == TicketStatus.WAITING) {
                ticketRepository
                    .findWaitingByTypeOrderedByCreatedAt(ticket.type)
                    .indexOfFirst { it.id == ticket.id } + 1
            } else {
                0
            }

        return QueueStatusResponse(
            ticketNumber = ticketDetails.ticketName,
            status = ticketDetails.status,
            type = ticketDetails.type,
            positionInQueue = position,
            queueSize = queueSize,
            roomId = ticket.roomId,
            calledAt = ticket.calledAt,
        )
    }

    fun getAvailableTypes(): List<TicketType> = ticketRepository.findAllWaitingOrderedByCreatedAt().map { it.type }.distinct()

    @Transactional
    fun pairNextPatient(
        type: TicketType,
        roomId: Long,
        roomName: String,
        doctorId: Long,
    ): VisitResponse? {
        val ticket =
            ticketRepository
                .findOldestWaitingByTypeForUpdate(type, PageRequest.of(0, 1))
                .firstOrNull() ?: return null

        val calledAt = Date()
        ticket.status = TicketStatus.CALLED
        ticket.calledAt = calledAt
        ticket.roomId = roomId
        ticket.doctorId = doctorId
        val updated = ticketRepository.save(ticket)

        val visitEndsAt = Instant.ofEpochMilli(calledAt.time + VISIT_DURATION_MS).toString()

        queueService.broadcastPatientCalled(updated.ticketName!!, roomName, visitEndsAt)
        broadcastQueueUpdateForType(type)

        return VisitResponse(ticket = toTicketDetails(updated), visitEndsAt = visitEndsAt)
    }

    fun completeTicket(ticketId: Long): TicketDetails? {
        val ticket =
            ticketRepository
                .findById(ticketId)
                .orElse(null) ?: return null

        ticket.status = TicketStatus.COMPLETED
        val updated = ticketRepository.save(ticket)

        getQueueStatus(updated.ticketName!!)?.let(queueService::broadcastQueueUpdate)

        return toTicketDetails(updated)
    }

    @Transactional
    fun cancelTicket(ticketName: String): TicketDetails {
        val ticket =
            ticketRepository.findByTicketName(ticketName)
                ?: throw NoSuchElementException("Ticket not found")

        if (ticket.status != TicketStatus.WAITING) {
            throw IllegalStateException("Only waiting tickets can be cancelled")
        }

        ticket.status = TicketStatus.CANCELLED
        val updated = ticketRepository.save(ticket)

        broadcastQueueUpdateForType(ticket.type)

        return toTicketDetails(updated)
    }

    @Transactional
    fun completeExpiredVisits() {
        val cutoff = Date(System.currentTimeMillis() - VISIT_DURATION_MS)
        ticketRepository.findCalledBefore(cutoff).forEach { ticket ->
            ticket.status = TicketStatus.COMPLETED
            val updated = ticketRepository.save(ticket)
            getQueueStatus(updated.ticketName!!)?.let(queueService::broadcastQueueUpdate)
        }
    }

    private fun generateTicketNumber(type: TicketType): String {
        val prefix =
            when (type) {
                TicketType.CONSULTATION -> "C"
                TicketType.CHECKUP -> "K"
                TicketType.URGENT -> "U"
            }
        val randomPart = Random.nextInt(TICKET_RANDOM_MAX).toString().padStart(TICKET_NUMBER_LENGTH, '0')
        return "$prefix-$randomPart"
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

    private fun broadcastQueueUpdateForType(type: TicketType) {
        ticketRepository.findWaitingByTypeOrderedByCreatedAt(type).forEach { waitingTicket ->
            getQueueStatus(waitingTicket.ticketName!!)?.let(queueService::broadcastQueueUpdate)
        }
    }
}
