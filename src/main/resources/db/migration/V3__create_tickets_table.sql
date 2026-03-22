CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP,
    room_id BIGINT,
    CONSTRAINT fk_tickets_room
        FOREIGN KEY (room_id)
        REFERENCES rooms (id)
        ON DELETE SET NULL,
    doctor_id BIGINT,
    CONSTRAINT fk_tickets_doctor
        FOREIGN KEY (doctor_id)
        REFERENCES users (id)
        ON DELETE SET NULL
);