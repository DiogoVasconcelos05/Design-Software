-- payment_invoicing: tabelas privadas ao módulo (ADR-005 §1). Instantes em milissegundos desde a época (UTC);
-- valores em cêntimos de euro.
CREATE TABLE IF NOT EXISTS payment_methods (
    user_id       TEXT    PRIMARY KEY,
    kind          TEXT    NOT NULL,
    token         TEXT    NOT NULL,
    registered_at INTEGER NOT NULL
);

-- ADR-004 §4: no máximo uma cobrança por viagem (o trip_id é a chave de idempotência)
CREATE TABLE IF NOT EXISTS payment_charges (
    trip_id          TEXT    PRIMARY KEY,
    user_id          TEXT    NOT NULL,
    pass_type        TEXT    NOT NULL,
    duration_minutes INTEGER NOT NULL,
    amount_cents     INTEGER NOT NULL,
    status           TEXT    NOT NULL,
    attempts         INTEGER NOT NULL DEFAULT 0,
    psp_reference    TEXT,
    last_error       TEXT,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_charges_status_updated ON payment_charges (status, updated_at);

-- Faturas guardadas durante 10 anos: nunca são apagadas (FF-23) e só o estado de comunicação à AT muda.
-- O invoice_number é a série sequencial; o trip_id UNIQUE garante no máximo uma fatura por viagem (ADR-004 §4).
CREATE TABLE IF NOT EXISTS invoices (
    invoice_number     INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id         TEXT    NOT NULL UNIQUE,
    trip_id            TEXT    NOT NULL UNIQUE,
    user_id            TEXT    NOT NULL,
    amount_cents       INTEGER NOT NULL,
    issued_at          INTEGER NOT NULL,
    at_status          TEXT    NOT NULL,
    at_reference       TEXT,
    at_attempts        INTEGER NOT NULL DEFAULT 0,
    at_last_attempt_at INTEGER,
    at_last_error      TEXT
);

CREATE INDEX IF NOT EXISTS idx_invoices_user_issued ON invoices (user_id, issued_at);
CREATE INDEX IF NOT EXISTS idx_invoices_at_status ON invoices (at_status);
