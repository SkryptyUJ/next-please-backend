package com.uj.nextplease.queue.controller

import com.uj.nextplease.queue.service.QueueService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/queue")
class QueueController(
    private val queueService: QueueService,
) {
    @GetMapping("/subscribe/{roomId}")
    fun subscribeToQueueUpdates(
        @PathVariable roomId: Long,
    ): SseEmitter = queueService.subscribe(roomId)
}
