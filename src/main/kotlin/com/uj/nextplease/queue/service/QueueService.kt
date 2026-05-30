package com.uj.nextplease.queue.service

import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.util.Constants
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Service
class QueueService {
    private val emitters = ConcurrentHashMap<Long, MutableList<SseEmitter>>()

    fun subscribe(roomId: Long): SseEmitter {
        val emitter = SseEmitter(Constants.SSE_TIMEOUT_MS)

        val roomEmitters = emitters.computeIfAbsent(roomId) { mutableListOf() }
        roomEmitters.add(emitter)

        emitter.onCompletion { roomEmitters.remove(emitter) }
        emitter.onTimeout { roomEmitters.remove(emitter) }
        emitter.onError { _ -> roomEmitters.remove(emitter) }

        return emitter
    }

    fun broadcastQueueUpdate(
        roomId: Long,
        status: QueueStatusResponse,
    ) {
        val roomEmitters = emitters[roomId] ?: return

        val iterator = roomEmitters.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            try {
                emitter.send(
                    SseEmitter
                        .event()
                        .id(status.ticketNumber)
                        .name(Constants.SSE_EVENT_QUEUE_UPDATE)
                        .data(status)
                        .build(),
                )
            } catch (e: IOException) {
                iterator.remove()
            }
        }
    }

    fun broadcastPatientCalled(
        roomId: Long,
        ticketNumber: String,
        roomNumber: String,
    ) {
        val roomEmitters = emitters[roomId] ?: return

        val iterator = roomEmitters.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            try {
                emitter.send(
                    SseEmitter
                        .event()
                        .id(ticketNumber)
                        .name(Constants.SSE_EVENT_PATIENT_CALLED)
                        .data(
                            mapOf(
                                Constants.SSE_DATA_TICKET_NUMBER to ticketNumber,
                                Constants.SSE_DATA_ROOM_NUMBER to roomNumber,
                            ),
                        ).build(),
                )
            } catch (_: IOException) {
                iterator.remove()
            }
        }
    }
}
