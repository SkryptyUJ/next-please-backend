-- Seed doctor accounts (password for both: "password123", bcrypt-hashed) and a pool of free rooms.
INSERT INTO users (email, password, role, name, surname) VALUES
    ('doctor1@clinic.com', '$2a$10$vLIhcozaHi6J6/vfcf5Wre20tnorYp5K/C5DMDVzLloQvmKb894q2', 'DOCTOR', 'Anna', 'Kowalska'),
    ('doctor2@clinic.com', '$2a$10$h5G3u0AdLru/MlUi0Sxd/ektWu8DHrU1wCvaFCYLd8sTetPjogRlK', 'DOCTOR', 'Piotr', 'Nowak');

-- Free rooms: nobody seated yet, so doctor_id IS NULL and is_active = false.
INSERT INTO rooms (name, is_active, doctor_id) VALUES
    ('Room 1', false, NULL),
    ('Room 2', false, NULL),
    ('Room 3', false, NULL),
    ('Room 4', false, NULL);

-- Demo waiting tickets so a doctor has someone to call (no room/doctor until paired).
INSERT INTO tickets (ticket_number, status, type, created_at, room_id, doctor_id) VALUES
    ('C-101', 'WAITING', 'CONSULTATION', CURRENT_TIMESTAMP - INTERVAL '3 minutes', NULL, NULL),
    ('K-201', 'WAITING', 'CHECKUP', CURRENT_TIMESTAMP - INTERVAL '2 minutes', NULL, NULL),
    ('U-301', 'WAITING', 'URGENT', CURRENT_TIMESTAMP - INTERVAL '1 minutes', NULL, NULL);
