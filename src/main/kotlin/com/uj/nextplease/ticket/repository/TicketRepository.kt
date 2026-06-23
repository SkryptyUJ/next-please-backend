package com.uj.nextplease.ticket.repository

import com.uj.nextplease.ticket.Ticket
import com.uj.nextplease.ticket.model.TicketStatus
import com.uj.nextplease.ticket.model.TicketType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Date

interface TicketRepository : JpaRepository<Ticket, Long> {
    fun findByTicketName(ticketName: String): Ticket?

    fun findByStatus(status: TicketStatus): List<Ticket>

    fun findByRoomIdAndStatus(
        roomId: Long,
        status: TicketStatus,
    ): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING' ORDER BY t.createdAt ASC")
    fun findAllWaitingOrderedByCreatedAt(): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.type = :type AND t.status = 'WAITING' ORDER BY t.createdAt ASC")
    fun findWaitingByTypeOrderedByCreatedAt(type: TicketType): List<Ticket>

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.type = :type AND t.status = 'WAITING'")
    fun countWaitingByType(type: TicketType): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.type = :type AND t.status = 'WAITING' ORDER BY t.createdAt ASC")
    fun findOldestWaitingByTypeForUpdate(
        type: TicketType,
        pageable: Pageable,
    ): List<Ticket>

    @Query("SELECT t FROM Ticket t WHERE t.status = 'CALLED' AND t.calledAt <= :cutoff")
    fun findCalledBefore(cutoff: Date): List<Ticket>
}
