# Back-end Agent Brief — next-please

You are implementing the backend for a **medical queue simulation**. Read this brief and
[`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md) (the binding contract) before editing. Also obey
the repo conventions in `CLAUDE.md` (constructor injection only, `ResponseEntity<T>` with explicit
status, DTOs as `data class` suffixed `Request`/`Response`, schema changes via Flyway migrations,
run `./gradlew spotlessApply` before finishing).

## Mission

Evolve the current three-role, per-room app into a **two-role (DOCTOR + PATIENT)** app with a
**global per-type queue** and a fixed **20-second** auto-completing visit.

## The two flows you must support

- **Doctor:** login → `claim` a free room → pick a `type` → backend instantly pairs him with the
  oldest `WAITING` ticket of that type (→ `CALLED`, room/doctor assigned, `visitEndsAt = now+20s`)
  → after 20 s the ticket auto-completes → doctor picks a type again → `release` on logout (room
  returns to the free pool).
- **Patient:** no login; choose a `type` → wait in the per-type queue → when paired, a 20 s visit
  runs → done. May `cancel` (leave) only while `WAITING`.

## Tasks (ordered)

1. **Roles** — remove `ADMIN` from `user/model/UserRole.kt`; remove `ROLE_ADMIN` from
   `util/Constants.kt`. Add `const val VISIT_DURATION_SECONDS = 20`.
2. **Security** — rewrite `config/SecurityConfig.kt` authorization to the matrix in spec §3.
   Public: login, ticket create, `/api/auth/token/**`. `ROLE_PATIENT`: `GET /api/tickets/status/**`,
   `POST /api/tickets/*/cancel`, `GET /api/queue/subscribe`. `ROLE_DOCTOR`: `GET /api/rooms/available`,
   `/api/rooms/*/claim`, `/api/rooms/*/release`, `/api/doctors/**`. Delete `/api/admin/**` and the
   `ROLE_ADMIN` matchers. (Declare specific matchers before broad ones.)
3. **Ticket creation (type-only)** — `TicketCreateRequest` becomes `{ type }`; drop `roomId`.
   `TicketCreateResponse` drops `roomId`. In `TicketService.createTicket`, save with
   `roomId = null`, `doctorId = null`, `status = WAITING`. Numbering: type prefix
   (`CONSULTATION→"C"`, `CHECKUP→"K"`, `URGENT→"U"`) + `-` + 3-digit zero-padded random.
4. **Per-type queue** — add `TicketRepository.findWaitingByTypeOrderedByCreatedAt(type)` and
   `countWaitingByType(type)`. Rewrite `getQueueStatus` so `positionInQueue`/`queueSize` are
   computed within the ticket's `type` (global), not by room. Add nullable `calledAt` to
   `QueueStatusResponse`.
5. **Pairing** — `getAvailableTypes()` returns the **global** distinct types among `WAITING`
   tickets. `next-patient?type=` selects the oldest `WAITING` of that type, sets `status=CALLED`,
   `calledAt=now`, `roomId=<doctor's room>`, `doctorId=<doctor>`; compute
   `visitEndsAt = calledAt + VISIT_DURATION_SECONDS`. Return `VisitResponse{ ticket: TicketDetails,
   visitEndsAt }`. 404 if none waiting. Make the select-and-claim **atomic** (pessimistic lock on
   the oldest-of-type query, or a guarded conditional update) to survive two doctors picking the
   same type.
6. **Rooms / self-service** — add `GET /api/rooms/available` →
   `RoomRepository.findByDoctorIdIsNull()`. Add `POST /api/rooms/{id}/claim` (sets `doctor_id=me,
   is_active=true`; 409 if room taken or doctor already seated elsewhere — reuse the uniqueness
   checks currently in `assignDoctorToRoom`) and `POST /api/rooms/{id}/release` (sets
   `doctor_id=null, is_active=false`; if the doctor has a ticket in `CALLED`, complete it first; 403
   if the room isn't the caller's). Resolve the doctor from the principal exactly like
   `getAuthenticatedDoctorRoom()`. Remove `PUT /api/rooms/{id}` and `POST .../assign-doctor` from
   the API.
7. **Patient cancel** — add `POST /api/tickets/{ticketId}/cancel` (ownership: principal ==
   ticketId). `TicketService.cancelTicket` must **only** cancel when `status == WAITING`, else throw
   → controller returns **409**. Broadcast `queue-update` to the rest of that type.
8. **Auto-complete** — add `@EnableScheduling`; a `@Scheduled(fixedDelay = 1000)` task completes
   `CALLED` tickets where `now ≥ calledAt + VISIT_DURATION_SECONDS` (single transactional update),
   broadcasting a final `queue-update` (status `COMPLETED`) to the patient's emitter.
9. **SSE re-key** — change `QueueService` map key from `roomId: Long` to `ticketNumber: String`.
   `QueueController` `subscribe` derives the ticket from the patient token (drop `{roomId}` path).
   Add `broadcastToType(type)` that recomputes positions for all `WAITING` of the type and pushes
   each its own `queue-update`. `patient-called` payload gains `visitEndsAt`.
10. **Admin removal** — delete `createTicketAsAdmin` and the admin `cancelTicket` from
    `RoomController`.
11. **Seed** — `src/main/resources/db/migration/V4__seed_doctors_and_rooms.sql`: insert ≥2 doctor
    users (bcrypt hashes, `role='DOCTOR'`), several free rooms (`is_active=false, doctor_id=NULL`),
    optionally a few demo `WAITING` tickets (`room_id NULL`).

## Contracts you must not break

- Token shapes and `JwtService` are unchanged. Patient principal = ticket number.
- DTOs are `data class`; keep field names exactly as the frontend brief expects: `VisitResponse`,
  `visitEndsAt` (ISO-8601), `QueueStatusResponse{ticketNumber,status,type,positionInQueue,queueSize,
  roomId,calledAt}`, `patient-called` event `{ticketNumber,roomNumber,visitEndsAt}`.
- Keep `ddl-auto=validate`; any column change goes through a new `V*` migration (none required by
  this brief — `room_id`/`doctor_id`/`called_at` are already nullable).

## Definition of done

- `./gradlew spotlessApply build` is green (unit + Testcontainers integration tests).
- New/updated tests cover: type-only creation + numbering, per-type position, pairing happy-path and
  404-when-empty, concurrent double `next-patient` (only one wins), auto-complete after 20 s,
  `claim`/`release` incl. room returning to the free pool and completing an in-progress visit, patient
  cancel happy-path and **409** on non-`WAITING`.
- No reference to `ADMIN`/`/api/admin` remains. CORS still allows `http://localhost:3000`.

## Conventions reminder

Constructor injection only; no `@Autowired` fields. Per-controller try/catch → explicit `4xx/5xx`
(no global `@ControllerAdvice`). Test names use backtick `given/when/then`. Format before committing.
