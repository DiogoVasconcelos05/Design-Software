-- Isolamento de Tabela SQL (FF-21): Apenas tabelas do módulo station_inventory
CREATE TABLE IF NOT EXISTS stations (
    station_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    parish VARCHAR(128) NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS docks (
    dock_id VARCHAR(64) PRIMARY KEY,
    station_id VARCHAR(64) NOT NULL,
    bike_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);