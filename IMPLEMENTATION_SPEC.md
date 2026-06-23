# next-please — Implementation Spec: Two-Role Flow (Doctor + Patient)

**Status:** proposed · **Scope:** backend (Kotlin + Spring Boot) + frontend (Next.js) ·
**Supersedes:** the three-role model (ADMIN/DOCTOR/PATIENT) and the per-room queue.

Single source of truth for the simplified queue simulation. Agent-facing build instructions live
in [`FRONTEND_AGENT_BRIEF.md`](FRONTEND_AGENT_BRIEF.md) and
[`BACKEND_AGENT_BRIEF.md`](BACKEND_AGENT_BRIEF.md); this file is the contract both must honour.
English to match the codebase; the ADR/submission artefacts stay Polish.

---

## 1. Actors & the two flows

Only two actors. No `ADMIN`.

### Doctor (authenticated)
1. Logs in → receives a `DOCTOR` token.
2. Picks a **room** to sit in (dropdown of currently free rooms) → **claims** it.
3. Picks a **patient type** to see.
4. Picking a type **instantly pairs** the doctor with the oldest waiting patient of that type
   (the ticket moves to `CALLED`, and gets this doctor's `room_id` + `doctor_id`).
5. A fixed **20-second** visit runs ("treating the patient").
6. After 20 s the visit auto-completes; the patient leaves the queue.
7. The doctor returns to the type-picker and the loop repeats.
8. A **log out** button releases everything — including the room, which returns to the free pool.

### Patient (anonymous, cannot log in)
1. First screen: choose your **type** only (no room, no account).
2. Wait in the per-type queue while no doctor is seeing your type.
3. When you are first in line **and** a doctor picks your type, you move to a **visit screen**
   that lasts the fixed 20 s.
4. At any time while waiting you may **leave**, which removes you from the queue.

---

## 2. Key structural decision — global queue by type

The patient never chooses a room. Therefore:

- A ticket is created with **`room_id = NULL`, `doctor_id = NULL`, `status = WAITING`** and a `type`.
- The queue is **global, partitioned by `type`**, ordered by `created_at`. Position and queue size
  are computed **per type**, not per room.
- `room_id` and `doctor_id` are assigned **at call time**, when a doctor sitting in a room pairs
  with the ticket. (This matches the original intent noted in `CLAUDE.md`.)
- Ticket numbers can no longer use a room-name prefix. Use a **type prefix** instead:
  `CONSULTATION→"C"`, `CHECKUP→"K"`, `URGENT→"U"`, format `{PREFIX}-{NNN}` (3-digit, zero-padded).

---

## 3. Roles, tokens, security

`UserRole = { DOCTOR, PATIENT }`.

| Token | Subject | Role claim | Lifetime | Issued by |
|-------|---------|-----------|----------|-----------|
| Doctor | `email` | `DOCTOR` | `staffExpirationMs` (1 h) | `POST /api/auth/login` |
| Patient | `guest-{ticketNumber}` | `PATIENT` | `patientExpirationMs` (3 h) | `POST /api/tickets/create` |

`JwtAuthenticationFilter` is unchanged except the `else` branch only ever yields `DOCTOR`.
Patient endpoints are scoped to the caller's own ticket: principal (the ticket number) must equal
the `{ticketId}` in the path.

**Authorization matrix (target `SecurityConfig`):**

| Pattern | Access |
|---------|--------|
| `POST /api/auth/login` | `permitAll` |
| `POST /api/tickets/create` | `permitAll` |
| `POST /api/auth/token/**` | `permitAll` |
| `GET /api/tickets/status/**` | `ROLE_PATIENT` |
| `POST /api/tickets/*/cancel` | `ROLE_PATIENT` |
| `GET /api/queue/subscribe` | `ROLE_PATIENT` |
| `GET /api/rooms/available` | `ROLE_DOCTOR` |
| `POST /api/rooms/*/claim`, `POST /api/rooms/*/release` | `ROLE_DOCTOR` |
| `/api/doctors/**` | `ROLE_DOCTOR` |
| ~~`/api/admin/**`~~ | removed |

---

## 4. Ticket lifecycle

![Cykl życia biletu](diagrams/05-cykl-zycia-biletu.png)

```
WAITING ──(doctor picks type → pairing)──▶ CALLED (= visit, 20 s) ──(auto after 20 s)──▶ COMPLETED
   │
   └──(patient leaves)──▶ CANCELLED        only from WAITING
```

- `WAITING → CALLED`: doctor selects a type; backend pairs the oldest `WAITING` ticket of that type,
  sets `room_id`/`doctor_id`/`calledAt`, and computes `visitEndsAt = calledAt + 20 s`.
- `CALLED → COMPLETED`: **automatic** when `now ≥ visitEndsAt` (scheduler). No manual step required.
- `WAITING → CANCELLED`: patient leaves. Cancelling a non-`WAITING` ticket → **409**.
- `COMPLETED` and `CANCELLED` are terminal.

**Visit duration:** `Constants.VISIT_DURATION_SECONDS = 20` (hard-coded). Backend owns timing and
exposes `visitEndsAt`; both frontends count down from that value so they stay in sync.

---

## 5. Data model & provisioning

Structure unchanged (see `diagrams/02-baza-danych-erd.png`); `users.role ∈ {DOCTOR, PATIENT}`.
At creation a ticket has `room_id = NULL` and `doctor_id = NULL` (already nullable — no schema change
needed for that).

**Room availability:** a room is **free** when `doctor_id IS NULL`. Seed rooms with
`is_active = false, doctor_id = NULL`. Claiming sets `doctor_id = me, is_active = true`; releasing
resets to `doctor_id = NULL, is_active = false` (back in the free pool).

**Seed migration `V4__seed_doctors_and_rooms.sql`:** doctor users (email + bcrypt hash +
`role='DOCTOR'`) and a handful of free rooms. Optionally seed demo `WAITING` tickets
(`room_id NULL`) so a doctor has someone to call. Never store plaintext passwords.

---

## 6. API specification

### 6.1 Public (no token)

| Method | Path | Body | 2xx | Errors |
|--------|------|------|-----|--------|
| POST | `/api/auth/login` | `LoginRequest{email,password}` | `LoginResponse{token,email,name,surname,role}` | 401 |
| POST | `/api/tickets/create` | `TicketCreateRequest{type}` | `TicketCreateResponse{ticketNumber,token}` | 400 |
| POST | `/api/auth/token/{ticketId}` | — | `PatientTokenResponse{token,ticketId}` | 401 |

> `TicketCreateRequest` loses `roomId` (now `{type}` only). `TicketCreateResponse` drops `roomId`.

### 6.2 Doctor (`ROLE_DOCTOR`)

| Method | Path | Params | 2xx | Errors | Side effects |
|--------|------|--------|-----|--------|--------------|
| GET | `/api/rooms/available` | — | `List<RoomResponse>` (rooms with `doctor_id IS NULL`) | — | the room-picker dropdown |
| POST | `/api/rooms/{roomId}/claim` | — | `RoomResponse` | 404 · 409 already taken / doctor already seated | `doctor_id=me, is_active=true` |
| POST | `/api/rooms/{roomId}/release` | — | `RoomResponse` | 404 · 403 not mine | `doctor_id=NULL, is_active=false`; if a visit is in progress it is completed first |
| GET | `/api/doctors/room` | — | `RoomResponse` | 404 none claimed | caller's room |
| GET | `/api/doctors/available-types` | — | `List<TicketType>` | — | **global** distinct types among `WAITING` tickets |
| POST | `/api/doctors/next-patient` | `?type=CONSULTATION` | `VisitResponse{ticket: TicketDetails, visitEndsAt}` | 404 none waiting of type | pairs oldest `WAITING` of type → `CALLED`; sets room/doctor; SSE `patient-called`; SSE `queue-update` to remaining of that type |

`complete-patient/{ticketId}` (manual finish) may remain for completeness but is **not** part of the
happy path — completion is automatic at `visitEndsAt`.

### 6.3 Patient (`ROLE_PATIENT`, own ticket)

| Method | Path | 2xx | Errors | Side effects |
|--------|------|-----|--------|--------------|
| GET | `/api/queue/subscribe` | `SseEmitter` stream | — | personal stream keyed by the token's ticket number |
| GET | `/api/tickets/status/{ticketId}` | `QueueStatusResponse` | 403 not own · 404 | position is **per type** |
| POST | `/api/tickets/{ticketId}/cancel` | `TicketDetails` (CANCELLED) | 403 · 404 · **409 not WAITING** | `WAITING→CANCELLED`; SSE `queue-update` to remaining of type |

> Patient SSE no longer takes `{roomId}` — the patient has no room. The server derives the ticket
> from the patient token. `QueueStatusResponse` gains a nullable **`calledAt`** so a polling client
> can compute the 20 s countdown (`visitEndsAt = calledAt + 20 s`).

---

## 7. Real-time (SSE) contract

`QueueService` is re-keyed from `roomId` to **`ticketNumber`**:
`ConcurrentHashMap<String, SseEmitter>`. Helper `broadcastToType(type)` recomputes positions for all
`WAITING` tickets of a type and pushes each its own `queue-update`.

| Event | Trigger | Payload |
|-------|---------|---------|
| `queue-update` | created / paired / completed / cancelled affecting a type | `QueueStatusResponse{ticketNumber,status,type,positionInQueue,queueSize,roomId,calledAt}` |
| `patient-called` | doctor pairs with this ticket | `{ ticketNumber, roomNumber, visitEndsAt }` |

Emitter timeout `Constants.SSE_TIMEOUT_MS = 60_000`; removed on completion/timeout/error/`IOException`.
The doctor UI does **not** need SSE — it counts down locally from the `visitEndsAt` returned by
`next-patient`, and the backend auto-completes independently.

---

## 8. Auto-completion (the 20 s visit)

Enable scheduling (`@EnableScheduling`). A `@Scheduled(fixedDelay = 1000)` sweep finds `CALLED`
tickets where `now ≥ calledAt + VISIT_DURATION_SECONDS`, sets them `COMPLETED`, and broadcasts a
final `queue-update` (status `COMPLETED`) to that ticket's emitter so the patient screen ends the
visit. Use a single transactional update to avoid double-completion.

---

## 9. Concurrency

`next-patient` may be hit by two doctors picking the same type at once. Select-and-claim the oldest
`WAITING` of the type **atomically** — pessimistic lock (`@Lock(PESSIMISTIC_WRITE)` on the
"oldest waiting of type" query) or a guarded `UPDATE … WHERE status='WAITING'` returning the row.
If none remain, return 404 so the frontend refreshes the type list.

---

## 10. Concrete delta from current code (backend)

**Roles / security** — remove `ADMIN` from `UserRole`; rewrite `SecurityConfig` to the §3 matrix;
drop `ROLE_ADMIN` from `Constants`.

**Tickets** — `TicketCreateRequest{type}` (drop `roomId`); create with `roomId=NULL`; type-prefix
numbering (§2); `getQueueStatus` computes position/size **by type** (new repo methods
`findWaitingByTypeOrderedByCreatedAt`, `countWaitingByType`); add `calledAt` to
`QueueStatusResponse`; add patient `cancel` endpoint with `WAITING`-only guard (409 otherwise);
delete admin ticket endpoints.

**Doctor / pairing** — `getAvailableTypes` becomes global; `next-patient` pairs globally by type and
assigns room/doctor/`calledAt`, returns `VisitResponse{ticket, visitEndsAt}`; `available-types`
unchanged path.

**Rooms** — add `GET /api/rooms/available` (`findByDoctorIdIsNull`), `claim`, `release` (release
completes any in-progress visit); fold `assignDoctorToRoom` logic into `claim`; remove
`PUT /api/rooms/{id}` and `assign-doctor` from the API.

**Visit timing** — `Constants.VISIT_DURATION_SECONDS = 20`; `@EnableScheduling`; scheduled
auto-complete sweep (§8).

**SSE** — re-key `QueueService` by `ticketNumber`; `subscribe` derives ticket from token (drop the
`{roomId}` path); add `broadcastToType`; `patient-called` payload gains `visitEndsAt`.

**Provisioning** — `V4` seed migration (doctors + free rooms [+ demo tickets]).

**Tests** — adapt existing tests; add: pairing happy-path + 404 when empty, concurrency double-call,
auto-complete after 20 s, patient cancel happy-path + 409 on non-WAITING, claim/release incl.
free-pool return.

> Out of scope but tracked: `Dockerfile` healthcheck hits `/actuator/health` while the actuator
> starter is absent — add the dependency or change the check.

---

## 11. Reference diagrams

`diagrams/01-architektura-komponentow.*` (refresh: drop Admin + `/api/admin`),
`02-baza-danych-erd.*` (unchanged), `03-sekwencja-bilet-sse.*` (still valid for the call flow),
`04-wdrozenie-docker.*` (unchanged), `05-cykl-zycia-biletu.*` (this spec).

---

## 12. Decisions captured (for the Polish ADR set)

1. Two-role RBAC — drop `ADMIN`.
2. Global per-type queue; `room_id`/`doctor_id` assigned at pairing; type-prefixed numbering.
3. Doctor self-service room claim/release (free pool).
4. Fixed 20 s visit, backend-owned with scheduled auto-completion.
5. Per-ticket SSE; positions computed per type.
6. Patient-only cancellation, allowed only from `WAITING`.
