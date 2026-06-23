-- Seed an admin account (password: "password123", bcrypt-hashed). Admins are created by seed only;
-- there is no self-registration for admins.
INSERT INTO users (email, password, role, name, surname, status) VALUES
    ('admin@clinic.com', '$2a$10$vLIhcozaHi6J6/vfcf5Wre20tnorYp5K/C5DMDVzLloQvmKb894q2', 'ADMIN', 'System', 'Admin', 'ACTIVE');
