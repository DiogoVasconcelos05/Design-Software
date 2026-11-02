-- trip_management: tabelas privadas ao módulo (ADR-005 §1). Instantes em milissegundos desde a época (UTC).
CREATE TABLE IF NOT EXISTS trips (
    trip_id             TEXT    PRIMARY KEY,
    user_id             TEXT    NOT NULL,
    pass_type           TEXT    NOT NULL,
    status              TEXT    NOT NULL,
    origin_dock_id      TEXT    NOT NULL,
    destination_dock_id TEXT,
    bike_id             TEXT,
    requested_at        INTEGER NOT NULL,
    started_at          INTEGER,
    ended_at            INTEGER,
    -- ADR-004 §3: os eventId da doca ficam guardados com a viagem, com unicidade (segunda linha de defesa)
    start_event_id      TEXT    UNIQUE,
    close_event_id      TEXT    UNIQUE,
    -- Outbox de cobrança (ARCHITECTURE.md §7.4.2)
    billing_dispatched  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_trips_user_requested ON trips (user_id, requested_at);
CREATE INDEX IF NOT EXISTS idx_trips_bike_status ON trips (bike_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_status_requested ON trips (status, requested_at);
CREATE INDEX IF NOT EXISTS idx_trips_billing ON trips (status, billing_dispatched);
CREATE INDEX IF NOT EXISTS idx_trips_status_ended ON trips (status, ended_at);
