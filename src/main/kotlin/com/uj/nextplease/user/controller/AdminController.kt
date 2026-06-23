package com.uj.nextplease.user.controller

import com.uj.nextplease.user.model.DoctorResponse
import com.uj.nextplease.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val userService: UserService,
) {
    @GetMapping("/doctors/pending")
    fun getPendingDoctors(): ResponseEntity<List<DoctorResponse>> = ResponseEntity.ok(userService.getPendingDoctors())

    @GetMapping("/doctors")
    fun getAllDoctors(): ResponseEntity<List<DoctorResponse>> = ResponseEntity.ok(userService.getAllDoctors())

    @PostMapping("/doctors/{id}/approve")
    fun approveDoctor(
        @PathVariable id: Long,
    ): ResponseEntity<DoctorResponse> =
        try {
            val approved =
                userService.approveDoctor(id)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            ResponseEntity.ok(approved)
        } catch (_: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/doctors/{id}/reject")
    fun rejectDoctor(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        return try {
            if (!userService.rejectDoctor(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
            ResponseEntity.noContent().build()
        } catch (_: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @DeleteMapping("/users/{id}")
    fun deleteUser(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val requesterEmail =
            SecurityContextHolder.getContext().authentication?.principal as? String
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            if (!userService.deleteUser(id, requesterEmail)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
            ResponseEntity.noContent().build()
        } catch (_: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }
}
