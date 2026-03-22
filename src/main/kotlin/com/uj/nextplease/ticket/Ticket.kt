package com.uj.nextplease.ticket

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "tickets")
class Ticket(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
    var ticketName: String? = null,
    @Column(nullable = false, length = 50)
    var status: String? = null,
    @Column(nullable = false)
    var createdAt: Date? = null,
    @Column
    var calledAt: Date? = null,
    @Column
    var roomId: Long? = null,
    @Column(name = "doctor_id")
    var doctorId: Long? = null,
)
