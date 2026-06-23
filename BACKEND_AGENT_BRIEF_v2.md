# Back-end Agent Brief — next-please (Iteration 3)

You are extending the existing **two-role** backend (Kotlin + Spring Boot). The binding contract is
[`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md); obey the repo conventions in `CLAUDE.md`
(constructor injection only, `ResponseEntity<T>` with explicit status, DTOs as `data class`
`Request`/`Response`, Flyway for schema changes, run `./gradlew spotlessApply build` before finishing).

## What this iteration adds / changes

1. **Re-introduce `ADMIN`** as a third role, used to onboard doctors securely.
2. **Self-service doctor onboarding**: a public form creates a `PENDING` user **who sets their own
   password**; an admin approves or rejects. The admin never sets/sees a password.
3. **Admin god-mode**: list all doctors, delete any user.
4. **Remove the 20 s timer**; the doctor ends the visit with **"Stop consultation"**
   (`complete-patient`), authorized to the session doctor only.

## Tasks (ordered)

1. **Role + status**
   - Re-add `ADMIN` to `user/model/UserRole.kt`; re-add `ROLE_ADMIN` to `util/Constants.kt`.
   - Add a `status` to the `User` entity and `UserDetails`: `PENDING | ACTIVE`
     (enum stored as `VARCHAR`, like `TicketStatus`). No rejected state — see task 4.
   - Migration `V?__add_user_status.sql`: `ALTER TABLE users ADD COLUMN status VARCHAR(50) NOT NULL
     DEFAULT 'ACTIVE'` (existing users become `ACTIVE`); optional `CHECK` on the two values.

2. **Self-registration (public)**
   - DTO `RegisterDoctorRequest { email, name, surname, password }`.
   - `POST /api/auth/register-doctor` (permitAll) → `UserService.registerDoctor(...)`: create
     `role = DOCTOR`, `status = PENDING`, `password = passwordEncoder.encode(...)`. Enforce unique
     email. Return **201** with a **neutral** body (don't reveal whether the email already existed).

3. **Login gate**
   - In `AuthController.login` / `UserService`: after credential check, if `status != ACTIVE`
     return **403** (distinct from 401 bad creds). Login now also returns `role = ADMIN` for admins.

4. **Admin module** (`admin` controller + service, or extend `user`; all `ROLE_ADMIN`)
   - `GET /api/admin/doctors/pending` → users with `role=DOCTOR AND status=PENDING`.
   - `POST /api/admin/doctors/{id}/approve` → `PENDING → ACTIVE` (409 if not `PENDING`).
   - `POST /api/admin/doctors/{id}/reject` → **hard-delete the user** (409 if not `PENDING`). No
     soft-delete; the row is removed so the email frees up.
   - `GET /api/admin/doctors` → all users with `role=DOCTOR` (any status), for the overview list.
   - `DELETE /api/admin/users/{id}` → hard delete. **Guards:** cannot delete yourself; refuse to
     delete the last `ACTIVE` admin (return 409). Resolve the caller from the principal (email).
   - Repository: add `findByRoleAndStatus`, `findByRole`, and a count of active admins as needed.

5. **Security** (`config/SecurityConfig.kt`)
   - Add `POST /api/auth/register-doctor` to `permitAll`.
   - Add `/api/admin/**` → `hasRole(ADMIN)`.
   - Keep the patient/doctor matchers from the previous iteration. Declare specific matchers before
     broad ones.

6. **Remove the visit timer**
   - Delete `Constants.VISIT_DURATION_SECONDS`, `visitEndsAt` (from `VisitResponse`/`next-patient`
     return and the `patient-called` payload), `@EnableScheduling`, and the `@Scheduled`
     auto-complete sweep. `next-patient` now returns the paired `TicketDetails`.

7. **"Stop consultation" = complete-patient**
   - `POST /api/doctors/complete-patient/{ticketId}` becomes the primary terminator. In
     `TicketService.completeTicket`, require: the ticket's `doctorId == authenticated doctor's id`
     **and** `status == CALLED`. Wrong doctor → **403**; wrong state → **409**. On success set
     `COMPLETED` and broadcast `queue-update` (status `COMPLETED`) to the patient's emitter.
   - Resolve the authenticated doctor from the principal (email → `userRepository.findByEmail`),
     same pattern as `getAuthenticatedDoctorRoom()`.

8. **Seed admin**
   - Migration `V?__seed_admin.sql`: ≥1 `ADMIN` user (bcrypt hash, `status='ACTIVE'`). Keep existing
     doctor/room seeds (seeded doctors `ACTIVE`).

## Carried-over behavior you must keep

Global per-type queue (ticket created with `room_id/doctor_id = NULL`, type-prefixed numbering,
per-type position/size); `next-patient` pairs the oldest `WAITING` of a type atomically and assigns
room/doctor; room `claim`/`release` (release completes any in-progress visit); patient `cancel`
(`WAITING`-only, 409 otherwise); per-ticket SSE (`queue-update`, `patient-called`).

## Contracts the frontend depends on (don't rename)

- `register-doctor` request `{ email, name, surname, password }`; neutral 201.
- Login 403 when not `ACTIVE`; `LoginResponse.role ∈ {ADMIN, DOCTOR}`.
- Admin list item shape includes at least `{ id, email, name, surname, status }`.
- `patient-called` event `{ ticketNumber, roomNumber }` (no `visitEndsAt`).
- `complete-patient` → 200 `TicketDetails` (COMPLETED); 403 wrong doctor; 409 not `CALLED`.

## Definition of done

- `./gradlew spotlessApply build` green (unit + Testcontainers).
- Tests added: register → `PENDING`; login blocked (403) while `PENDING`, allowed after approve;
  approve/reject guards (409 when not `PENDING`); **reject deletes the row (later login → 401)**;
  delete-user self-guard and last-active-admin guard;
  `complete-patient` by a non-session doctor → 403; `complete-patient` when not `CALLED` → 409;
  happy-path stop consultation broadcasts `COMPLETED`.
- No `visitEndsAt`/scheduler remains. `ddl-auto=validate` still holds (new `status` column via the
  migration above). CORS still allows `http://localhost:3000`.
