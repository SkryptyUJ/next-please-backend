# next-please — Implementation Spec (Iteration 3: Admin + Self-Service Onboarding + Manual Stop)

**Status:** proposed · **Scope:** backend (Kotlin + Spring Boot) + frontend (Next.js) ·
**Builds on:** the two-role global-by-type queue you already shipped.

What changes in this iteration, at a glance:

1. **ADMIN returns** — a third role with a dedicated login + panel, used to securely onboard doctors.
2. **Self-service doctor onboarding** — a public "request an account" form creates a `PENDING`
   user **who sets their own password**; an admin approves (→ active doctor) or rejects. The admin
   never sees or sets a password.
3. **Admin god-mode** — list all doctors and delete any user.
4. **No more 20 s timer** — the visit ends when the **doctor clicks "Stop consultation"**. The
   button is visible only to the doctor running that session; the backend enforces the same.

Agent build instructions: [`FRONTEND_AGENT_BRIEF.md`](FRONTEND_AGENT_BRIEF.md),
[`BACKEND_AGENT_BRIEF.md`](BACKEND_AGENT_BRIEF.md). English to match the codebase; ADR/submission
artefacts stay Polish.

---

## 1. Roles, accounts, tokens

`UserRole = { ADMIN, DOCTOR, PATIENT }` (ADMIN re-added).

New column **`users.status`** with values `PENDING | ACTIVE`:

- Admins and any seeded doctors are `ACTIVE`.
- A self-registered doctor starts `PENDING` and **cannot log in** until `ACTIVE`.
- There is no rejected state — **rejecting a request deletes the user immediately** (no soft-delete).

| Token | Subject | Role claim | Lifetime | Issued by |
|-------|---------|-----------|----------|-----------|
| Staff (ADMIN or DOCTOR) | `email` | `ADMIN` / `DOCTOR` | `staffExpirationMs` (1 h) | `POST /api/auth/login` |
| Patient | `guest-{ticketNumber}` | `PATIENT` | `patientExpirationMs` (3 h) | `POST /api/tickets/create` |

**Login gate:** `POST /api/auth/login` succeeds only when the user's `status = ACTIVE`. A `PENDING`
user gets **403** (distinct from 401 bad-credentials) so the UI can say "awaiting approval". A
rejected user no longer exists, so a login attempt simply 401s. The single login endpoint serves
both admin and doctor; the returned `role` tells the frontend which panel to open.

---

## 2. Doctor onboarding (solves the password problem)

The previous concern was "the admin shouldn't set/know doctor passwords." This flow removes that
entirely: **the requester sets their own password**; the admin only changes `status`.

![Cykl życia konta lekarza](diagrams/06-cykl-konta-doktora.png)

1. A prospective doctor opens the **"Request an account"** form on the doctor app and submits
   `POST /api/auth/register-doctor { email, name, surname, password }`.
2. The backend creates a `users` row: `role = DOCTOR`, `status = PENDING`, `password = bcrypt(...)`.
   Email must be unique; the response is a neutral "request submitted, pending approval" (do not
   reveal whether the email already existed).
3. The admin sees the request in the panel and **approves** (`status → ACTIVE`) or **rejects**
   (**the user row is deleted immediately**). On approval the doctor can immediately log in with the
   password they chose — the admin never handled it.

> This is the secure default. (If you ever want zero stored passwords, SSO/OIDC is the alternative,
> but it's out of scope here.)

---

## 3. Admin capabilities

Admin accounts are created by **seed only** (no self-registration for admins).

| Method | Path | Purpose | Notes |
|--------|------|---------|-------|
| GET | `/api/admin/doctors/pending` | list `PENDING` doctor requests | the approval queue |
| POST | `/api/admin/doctors/{id}/approve` | `PENDING → ACTIVE` | 409 if not currently `PENDING` |
| POST | `/api/admin/doctors/{id}/reject` | **delete the pending user** | 409 if not currently `PENDING` |
| GET | `/api/admin/doctors` | list all doctors (any status) | god-mode overview |
| DELETE | `/api/admin/users/{id}` | hard-delete any user | guard: cannot delete self; refuse to delete the last `ACTIVE` admin |

All `/api/admin/**` require `ROLE_ADMIN`.

---

## 4. The visit: manual "Stop consultation" (timer removed)

Everything about the 20 s auto-complete is gone: remove `VISIT_DURATION_SECONDS`, `visitEndsAt`,
`@EnableScheduling`, and the scheduled sweep.

![Cykl życia biletu](diagrams/05-cykl-zycia-biletu.png)

```
WAITING ──(doctor picks type → pairing)──▶ CALLED (= in consultation) ──(doctor: Stop consultation)──▶ COMPLETED
   │
   └──(patient leaves)──▶ CANCELLED        only from WAITING
```

- `WAITING → CALLED`: doctor picks a type; backend pairs the oldest `WAITING` of that type and sets
  `room_id`/`doctor_id`/`calledAt`. `next-patient` returns the paired `TicketDetails`.
- `CALLED → COMPLETED`: **doctor clicks "Stop consultation"** →
  `POST /api/doctors/complete-patient/{ticketId}`. **Authorization:** only the doctor whose
  `doctor_id` equals the ticket's `doctor_id` (the one running the session) may complete it, and only
  while `status = CALLED`; otherwise **403/409**. This is what makes the button "visible/usable only
  to the doctor in session".
- `WAITING → CANCELLED`: patient leaves; cancelling a non-`WAITING` ticket → **409**.

`patient-called` payload drops `visitEndsAt` → `{ ticketNumber, roomNumber }`. The patient's visit
screen shows "in consultation — please wait" and ends when it receives the `queue-update` with
`status = COMPLETED` (sent when the doctor stops). No client countdown.

---

## 5. Global per-type queue (unchanged from last iteration)

Patient picks only a `type`; ticket is created with `room_id = NULL`, `doctor_id = NULL`,
`status = WAITING`. Queue is global, partitioned by `type`, ordered by `created_at`; position and
size are per type. `room_id`/`doctor_id` are assigned at pairing. Ticket numbers use a type prefix
(`CONSULTATION→C`, `CHECKUP→K`, `URGENT→U`) + `-` + 3-digit zero-padded random.

---

## 6. API surface (delta this iteration)

**New — public**

- `POST /api/auth/register-doctor { email, name, surname, password }` → 201 neutral confirmation.

**New — admin (`ROLE_ADMIN`)**

- `GET /api/admin/doctors/pending`, `POST /api/admin/doctors/{id}/approve`,
  `POST /api/admin/doctors/{id}/reject`, `GET /api/admin/doctors`, `DELETE /api/admin/users/{id}`
  (see §3).

**Changed**

- `POST /api/auth/login` — add the `status = ACTIVE` gate (403 otherwise); now returns `ADMIN` too.
- `POST /api/doctors/complete-patient/{ticketId}` — promoted from optional to the **primary** visit
  terminator ("Stop consultation"); add the session-owner authorization + `CALLED`-only guard.

**Removed**

- `VISIT_DURATION_SECONDS`, `visitEndsAt` (from responses and the `patient-called` event), the
  scheduler and `@EnableScheduling`.

Unchanged: ticket create (`{type}`), `next-patient`, `available-types`, room `claim`/`release`,
`GET /api/rooms/available`, patient `subscribe`/`status`/`cancel`, per-ticket SSE.

**Authorization matrix (target):**

| Pattern | Access |
|---------|--------|
| `POST /api/auth/login`, `POST /api/tickets/create`, `POST /api/auth/register-doctor`, `POST /api/auth/token/**` | `permitAll` |
| `GET /api/tickets/status/**`, `POST /api/tickets/*/cancel`, `GET /api/queue/subscribe` | `ROLE_PATIENT` |
| `GET /api/rooms/available`, `/api/rooms/*/claim`, `/api/rooms/*/release`, `/api/doctors/**` | `ROLE_DOCTOR` |
| `/api/admin/**` | `ROLE_ADMIN` |

---

## 7. Data model & migrations

- **New migration** `V?__add_user_status.sql`: add `status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'`
  to `users` (existing rows become `ACTIVE`). Optionally add a CHECK constraint for the two values
  (`PENDING`, `ACTIVE`).
- **Seed migration** `V?__seed_admin.sql`: at least one `ADMIN` user (bcrypt hash, `status='ACTIVE'`).
  Keep/extend any doctor + room seed from before (seeded doctors are `ACTIVE`).
- No other schema change: `room_id`/`doctor_id`/`called_at` on tickets are already nullable.

---

## 8. Concrete delta from the current (two-role) code

**Roles / status / auth**

- Re-add `ADMIN` to `UserRole`; re-add `ROLE_ADMIN` constant.
- Add `status` to the `User` entity + `UserDetails`; new enum/values `PENDING|ACTIVE`.
- `UserService`/`AuthController.login`: reject non-`ACTIVE` (i.e. `PENDING`) with 403.
- `register-doctor` endpoint + service: create `DOCTOR` + `PENDING` + bcrypt password; unique email;
  neutral response.

**Admin module** (new `admin` controller/service or extend `user`)

- Pending list, approve, **reject (hard-deletes the pending user)**, all-doctors list, delete-user
  (with self / last-admin guards).

**Security**

- Re-add `/api/admin/**` → `hasRole(ADMIN)`; add `/api/auth/register-doctor` to `permitAll`.

**Visit**

- Delete the scheduler, `@EnableScheduling`, `VISIT_DURATION_SECONDS`, `visitEndsAt` everywhere.
- `complete-patient`: require `ticket.doctorId == authenticated doctor id` and `status == CALLED`
  (else 403/409); on success `COMPLETED` + broadcast `queue-update`.
- `next-patient` returns `TicketDetails` (no `visitEndsAt`); `patient-called` event loses it too.

**Tests**

- register → PENDING; login blocked while PENDING (403) then allowed after approve; approve/reject
  state guards (409 if not PENDING); **reject deletes the row (subsequent login → 401)**; delete-user
  incl. self / last-admin guards; complete-patient by the wrong doctor → 403; complete-patient when
  not CALLED → 409; happy-path stop consultation.

> Still tracked, out of scope: `Dockerfile` healthcheck hits `/actuator/health` without the actuator
> starter on the classpath.

---

## 9. Decisions captured (for the Polish ADR set)

1. Three-role RBAC (`ADMIN/DOCTOR/PATIENT`); admin seeded only.
2. Self-service doctor onboarding with admin approval; requester sets own password, account gated by
   `status` (`PENDING`/`ACTIVE`) — secure provisioning without the admin handling secrets. Rejection
   hard-deletes the request (no soft-delete).
3. Manual visit termination via "Stop consultation", authorized to the session doctor; removed the
   timed auto-completion.
4. (Carried over) global per-type queue; call-time room/doctor assignment; per-ticket SSE;
   patient-only cancel from `WAITING`.
