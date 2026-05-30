package com.uj.nextplease.ticket.repository

import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TicketRepository : JpaRepository<Ticket, Long> {
    fun findByTicketName(ticketName: String): Ticket?

    fun findByStatus(status: TicketStatus): List<Ticket>

    fun findByRoomIdAndStatus(
        roomId: Long,
        status: TicketStatus,
    ): List<Ticket>

    fun findByRoomIdAndStatusAndType(
        roomId: Long,
        status: TicketStatus,
        type: TicketType,
    ): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING' ORDER BY t.createdAt ASC")
    fun findAllWaitingOrderedByCreatedAt(): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.roomId = :roomId AND t.status = 'WAITING' ORDER BY t.createdAt ASC")
    fun findWaitingByRoomIdOrderedByCreatedAt(roomId: Long): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.roomId = :roomId AND t.status = 'WAITING' AND t.type = :type ORDER BY t.createdAt ASC")
    fun findWaitingByRoomIdAndTypeOrderedByCreatedAt(
        roomId: Long,
        type: TicketType,
    ): List<Ticket>

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.roomId = :roomId AND t.status = 'WAITING'")
    fun countWaitingByRoomId(roomId: Long): Int
}
