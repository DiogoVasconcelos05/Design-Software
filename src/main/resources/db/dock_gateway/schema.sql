-- dock_gateway: tabelas privadas ao módulo (ADR-005 §1). Instantes em milissegundos desde a época (UTC).
-- ADR-004 §2: eventId já processados, com unicidade (primeira linha de defesa); purgados após 48 h.
CREATE TABLE IF NOT EXISTS dock_processed_events (
    event_id    TEXT    PRIMARY KEY,
    dock_id     TEXT    NOT NULL,
    event_type  TEXT    NOT NULL,
    occurred_at INTEGER NOT NULL,
    received_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dock_processed_events_received ON dock_processed_events (received_at);
