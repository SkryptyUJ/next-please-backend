-- Account lifecycle status. Existing users (seeded doctors) become ACTIVE.
-- Self-registered doctors start PENDING until an admin approves them.
ALTER TABLE users ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users ADD CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'ACTIVE'));
