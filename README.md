# Next Please — Medical Queue Management System

A backend service for managing patient queues in healthcare clinics. Patients take numbered tickets, doctors call the next patient from their assigned room, and waiting screens receive real-time updates via Server-Sent Events.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin (JVM 24) |
| Framework | Spring Boot 4.0.4 |
| Database | PostgreSQL 16+ |
| Migrations | Flyway |
| Auth | JWT (JJWT 0.13.0) + Spring Security |
| Real-time | Server-Sent Events (SSE) |
| Build | Gradle (Kotlin DSL) |
| Formatting | Spotless + ktlint 1.8.0 |

## Getting Started

### Prerequisites

- JDK 24 (Eclipse Temurin recommended)
- Docker (for PostgreSQL)

### Run Locally

Spring Boot's Docker Compose integration starts PostgreSQL automatically when the app is run via Gradle:

```bash
./gradlew bootRun
```

The app will be available at `http://localhost:8080`.

To run with a manually started database:

```bash
docker-compose up -d postgres
./gradlew bootRun
```

### Run with Docker Compose (Full Stack)

```bash
docker-compose up --build
```

This starts both PostgreSQL and the application on port `8080`.

## Development Commands

```bash
./gradlew build              # Build and run all tests
./gradlew build -x test      # Build without tests
./gradlew test               # Run tests only
./gradlew spotlessApply      # Format code (run before committing)
./gradlew spotlessCheck      # Check formatting without applying
```

Run a single test class:

```bash
./gradlew test --tests "com.uj.nextplease.ticket.service.TicketServiceTest"
```

## Architecture

### User Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Manages rooms and approves/removes doctors |
| `DOCTOR` | Claims a room and calls patients |
| `PATIENT` | Anonymous; identified by ticket number |

### Module Layout

```
com.uj.nextplease/
├── user/       — Auth, login, User entity, UserRole/UserStatus
├── room/       — Room entity, doctor assignment
├── ticket/     — Ticket entity, queue logic, lifecycle
├── queue/      — SSE emitter management, real-time broadcasts
├── security/   — JWT filter, SecurityConfig, SecurityProperties
├── config/     — Spring beans (PasswordEncoder, etc.)
└── util/       — Constants (SSE event names, timeouts)
```

Each module follows a layered pattern: `controller → service → repository → entity + DTOs`.

### Ticket Lifecycle

```
WAITING → CALLED → COMPLETED
       ↘ CANCELLED
```

Ticket numbers are generated as `{FirstLetterOfRoomName}-{3-digit-zero-padded-random}` (e.g. `A-042`). Creating a ticket broadcasts a queue update via SSE.

### JWT Authentication

Two distinct token types:

- **Staff token** — subject is `email`, carries `role` claim (`ADMIN` or `DOCTOR`), expires in 1 hour.
- **Patient token** — subject is `guest-{ticketNumber}`, carries `role=PATIENT`, expires in 3 hours.

Public endpoints (no token required):
- `POST /api/auth/login`
- `POST /api/tickets/create`
- `POST /api/auth/token/{ticketId}`

### Real-Time (SSE)

`QueueService` maintains a `ConcurrentHashMap<Long, MutableList<SseEmitter>>` keyed by room ID. Two event types:

- `queue-update` — sent on ticket create/call/complete/cancel, carries `QueueStatusResponse`
- `patient-called` — sent when a doctor calls the next patient, carries ticket number + room number

Emitters time out after 60 seconds.

## API Reference

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/login` | Public | Staff login; returns JWT + user details |
| `POST` | `/api/auth/register-doctor` | Public | Self-service doctor registration (creates PENDING user) |
| `POST` | `/api/auth/token/{ticketId}` | Public | Issue patient JWT for a ticket number |

### Admin

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/admin/doctors/pending` | ADMIN | List doctors awaiting approval |
| `GET` | `/api/admin/doctors` | ADMIN | List all doctors |
| `POST` | `/api/admin/doctors/{id}/approve` | ADMIN | Approve a pending doctor |
| `POST` | `/api/admin/doctors/{id}/reject` | ADMIN | Reject and delete a pending doctor |
| `DELETE` | `/api/admin/users/{id}` | ADMIN | Delete a user |

### Rooms

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/rooms/available` | DOCTOR | List unclaimed rooms |
| `POST` | `/api/rooms/{roomId}/claim` | DOCTOR | Claim a room |
| `POST` | `/api/rooms/{roomId}/release` | DOCTOR | Release the current room |
| `GET` | `/api/doctors/room` | DOCTOR | Get the room assigned to the authenticated doctor |

### Doctor Operations

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/doctors/available-types` | DOCTOR | List ticket types with waiting patients |
| `POST` | `/api/doctors/next-patient?type={type}` | DOCTOR | Call the next patient of the given type |
| `POST` | `/api/doctors/complete-patient/{ticketId}` | DOCTOR | Mark a ticket as COMPLETED |

### Tickets

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/tickets/create` | Public | Create a ticket; returns ticket number + patient JWT |
| `GET` | `/api/tickets/status/{ticketId}` | PATIENT | Get queue position and ticket status |
| `POST` | `/api/tickets/{ticketId}/cancel` | PATIENT | Cancel a waiting ticket |

### Queue (SSE)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/queue/subscribe` | PATIENT | Subscribe to SSE stream for queue updates |

## Database

PostgreSQL with Flyway migrations (`src/main/resources/db/migration/`). Hibernate DDL is set to `validate` — all schema changes must go through a new migration file.

**Tables:** `users`, `rooms`, `tickets`

- `rooms.doctor_id` is unique — one doctor per room.
- `tickets.room_id` and `tickets.doctor_id` are populated at call time, not at creation time.

## Testing

| Type | Convention | Notes |
|------|-----------|-------|
| Unit | `*Test.kt` | Mockito for dependencies, no DB |
| Integration | `*IntegrationTest.kt` | `@SpringBootTest` + Testcontainers (PostgreSQL) |
| Persistence | `*PersistenceTest.kt` | Repository layer with real DB |
| Smoke | `NextPleaseApplicationContextSmokeTest` | Verifies context loads |

Test names use backtick strings with given/when/then style:
```kotlin
@Test
fun `given doctor has no room when calling next patient then returns 400`() { }
```

## Configuration

Key properties in `application.yaml`:

```yaml
security:
  secretKey: <base64-encoded HMAC-SHA256 key>
  staffExpirationMs: 3600000      # 1 hour
  patientExpirationMs: 10800000   # 3 hours
  cors:
    allowed-origins:
      - http://localhost:3000
```

CORS is configured to allow `http://localhost:3000` and `http://localhost:3001` by default.