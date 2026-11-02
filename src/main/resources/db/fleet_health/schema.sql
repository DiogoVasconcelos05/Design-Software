-- fleet_health: tabelas privadas ao módulo (ADR-005 §1). Instantes em milissegundos desde a época (UTC).
-- Anomalias ativas por bicicleta: uma anomalia só abre ordem de manutenção quando é nova (§7.4.3).
-- A telemetria em si não é guardada, para que as leituras a cada 30 s não disputem o escritor do SQLite.
CREATE TABLE IF NOT EXISTS fleet_active_anomalies (
    bike_id      TEXT    NOT NULL,
    anomaly_type TEXT    NOT NULL,
    detected_at  INTEGER NOT NULL,
    PRIMARY KEY (bike_id, anomaly_type)
);
