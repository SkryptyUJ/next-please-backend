package com.uj.nextplease.queue.service

import com.uj.nextplease.ticket.model.QueueStatusResponse
import com.uj.nextplease.util.Constants
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Service
class QueueService {
    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    fun subscribe(ticketNumber: String): SseEmitter {
        val emitter = SseEmitter(Constants.SSE_TIMEOUT_MS)

        emitters[ticketNumber] = emitter

        emitter.onCompletion { emitters.remove(ticketNumber, emitter) }
        emitter.onTimeout { emitters.remove(ticketNumber, emitter) }
        emitter.onError { _ -> emitters.remove(ticketNumber, emitter) }

        return emitter
    }

    fun broadcastQueueUpdate(status: QueueStatusResponse) {
        send(status.ticketNumber) { emitter ->
            emitter.send(
                SseEmitter
                    .event()
                    .id(status.ticketNumber)
                    .name(Constants.SSE_EVENT_QUEUE_UPDATE)
                    .data(status)
                    .build(),
            )
        }
    }

    fun broadcastPatientCalled(
        ticketNumber: String,
        roomNumber: String,
        visitEndsAt: String,
    ) {
        send(ticketNumber) { emitter ->
            emitter.send(
                SseEmitter
                    .event()
                    .id(ticketNumber)
                    .name(Constants.SSE_EVENT_PATIENT_CALLED)
                    .data(
                        mapOf(
                            Constants.SSE_DATA_TICKET_NUMBER to ticketNumber,
                            Constants.SSE_DATA_ROOM_NUMBER to roomNumber,
                            Constants.SSE_DATA_VISIT_ENDS_AT to visitEndsAt,
                        ),
                    ).build(),
            )
        }
    }

    private fun send(
        ticketNumber: String,
        sendEvent: (SseEmitter) -> Unit,
    ) {
        val emitter = emitters[ticketNumber] ?: return
        try {
            sendEvent(emitter)
        } catch (_: IOException) {
            emitters.remove(ticketNumber, emitter)
        }
    }
}
