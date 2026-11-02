-- maintenance_orders: tabelas privadas ao módulo (ADR-005 §1). Instantes em milissegundos desde a época (UTC).
CREATE TABLE IF NOT EXISTS maintenance_orders (
    order_id      TEXT    PRIMARY KEY,
    bike_id       TEXT    NOT NULL,
    fault_type    TEXT    NOT NULL,
    origin        TEXT    NOT NULL,
    status        TEXT    NOT NULL,
    description   TEXT,
    reported_by   TEXT    NOT NULL,
    technician_id TEXT,
    notes         TEXT,
    opened_at     INTEGER NOT NULL,
    started_at    INTEGER,
    completed_at  INTEGER
);

-- §7.4.3: no máximo uma ordem ativa por (bike_id, fault_type), mesmo com várias réplicas a abrir em simultâneo.
CREATE UNIQUE INDEX IF NOT EXISTS ux_maintenance_orders_active
    ON maintenance_orders (bike_id, fault_type) WHERE status <> 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_maintenance_orders_status_opened ON maintenance_orders (status, opened_at);
