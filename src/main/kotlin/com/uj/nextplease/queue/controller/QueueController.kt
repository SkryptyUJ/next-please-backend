package com.uj.nextplease.queue.controller

import com.uj.nextplease.queue.service.QueueService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/queue")
class QueueController(
    private val queueService: QueueService,
) {
    @GetMapping("/subscribe")
    fun subscribeToQueueUpdates(): SseEmitter {
        val ticketNumber = SecurityContextHolder.getContext().authentication?.principal as String
        return queueService.subscribe(ticketNumber)
    }
}
