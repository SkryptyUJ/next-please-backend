# Front-end Agent Brief — next-please (Iteration 3)

Extend the existing **Next.js (App Router) + React + TypeScript** client. Backend contract:
[`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md) §1–§6. Keep the existing patient and doctor flows;
this brief adds the **admin app**, a **doctor account-request form**, and replaces the **visit
countdown** with a manual **"Stop consultation"** control.

Setup unchanged: dev on port **3000**, `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`),
JWT sent as `Authorization: Bearer <token>`. Three route groups now: patient `/`, doctor `/doctor/*`,
admin `/admin/*`. Patients never log in.

## 1. New: Admin app (`/admin/*`)

1. **`/admin/login`** — email + password → `POST /api/auth/login`. On success **require
   `role === "ADMIN"`** (otherwise sign out and show an error). Store the admin token in an
   `AdminContext` (+ sessionStorage); guard all `/admin/*` routes (no admin token → redirect here).
2. **`/admin`** — the panel, two sections:
   - **Pending requests** — `GET /api/admin/doctors/pending`. Each row shows `email, name, surname`
     with **Approve** (`POST /api/admin/doctors/{id}/approve`) and **Deny**
     (`POST /api/admin/doctors/{id}/reject`) buttons. **Deny permanently deletes the request** — put
     it behind a confirm dialog and make the copy clear ("This permanently removes the request").
     Refresh the list after each action; a 409 means it was already handled — just refetch.
   - **All doctors** (god-mode) — `GET /api/admin/doctors`, showing `email, name, surname, status`
     with a **Delete** button (`DELETE /api/admin/users/{id}`) behind a confirm dialog. Surface the
     backend's guard errors (409 when trying to delete yourself or the last active admin).
   - A **Log out** button clears the admin token → `/admin/login`.

## 2. New: Doctor "Request an account" form

On the doctor app (a link on `/doctor/login`, e.g. route `/doctor/register`):

- Form fields: `email, name, surname, password` (+ confirm-password, client-side match check).
  **The doctor chooses their own password here** — this is the whole point; the admin never sets it.
- Submit → `POST /api/auth/register-doctor { email, name, surname, password }`. On 201 show a clear
  "Request submitted — an admin must approve your account before you can log in." Then return to
  `/doctor/login`. Keep the message neutral on errors (don't leak whether the email already exists).

Update `/doctor/login` handling: if `POST /api/auth/login` returns **403**, show "Your account is
awaiting admin approval." rather than a generic credentials error (401). (A rejected account no
longer exists, so it returns 401 like any unknown user.)

## 3. Changed: the visit screens (no countdown)

The 20 s timer is gone. The visit ends when the **doctor** clicks **Stop consultation**.

- **Doctor `/doctor/visit`** — show the paired patient (ticket number + type) and a prominent
  **"Stop consultation"** button. Clicking it → `POST /api/doctors/complete-patient/{ticketId}`; on
  200 return to `/doctor/types`. (No timer, no auto-redirect.) This screen is only reachable by the
  doctor running the session, so the button is naturally exclusive to them; the backend also enforces
  it (403 if not the session doctor) — handle that error defensively.
- **Patient `/visit`** — remove the countdown. Show "In consultation — please wait." Keep the SSE
  stream open; when a `queue-update` with `status === "COMPLETED"` arrives (sent when the doctor
  stops), go to `/done`. There is no "leave" button here (the visit is in progress).
- `patient-called` event no longer carries `visitEndsAt`; just use `{ ticketNumber, roomNumber }` to
  show which room to go to.

## Unchanged flows (keep as-is)

- **Patient:** `/` choose type → `POST /api/tickets/create {type}` → `/wait` (SSE position +
  Leave/cancel while waiting) → on `patient-called` → `/visit` → `/done`.
- **Doctor:** `/doctor/login` → `/doctor/room` (claim a free room) → `/doctor/types`
  (`available-types` → pick → `next-patient`) → `/doctor/visit` → back to types; **Log out** releases
  the room. SSE still via `@microsoft/fetch-event-source` (Bearer header) for the patient.

## Shared concerns (unchanged)

One `fetch` wrapper (base URL + Bearer + typed errors distinguishing 401/403/404/409); contexts
`PatientContext`, `DoctorContext`, **new `AdminContext`** persisted to sessionStorage; route guards
per role; SSE reconnect with backoff + status-poll fallback.

## Definition of done

- A new doctor can self-register; they cannot log in until an admin approves (403 message shown).
- Admin can log in to `/admin`, see pending requests, approve/deny them, see all doctors, and delete
  a user (with confirm + guard-error handling).
- Doctor visit screen has a working **Stop consultation** button and no countdown; clicking it ends
  the patient's visit (patient moves to `/done` via the SSE `COMPLETED` event).
- No countdown/timer remains anywhere. Works against the backend with default env and
  `npm run dev` on `localhost:3000`. README updated with the new routes.
