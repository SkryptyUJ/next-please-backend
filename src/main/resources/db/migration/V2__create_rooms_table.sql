CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    doctor_id BIGINT UNIQUE,
    CONSTRAINT fk_rooms_user
        FOREIGN KEY (doctor_id)
        REFERENCES users (id)
        ON DELETE SET NULL
);