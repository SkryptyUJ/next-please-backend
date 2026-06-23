package com.uj.nextplease.user

import com.uj.nextplease.user.model.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true)
    var email: String = "",
    @Column(nullable = false)
    var password: String = "",
    @Column(nullable = false, length = 50)
    var role: String = "",
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var surname: String = "",
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,
)
