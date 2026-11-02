CREATE TABLE IF NOT EXISTS ia_users (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    navegante_card_no VARCHAR(64) UNIQUE,
    pass_type VARCHAR(32) NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ia_users_email ON ia_users(email);
CREATE INDEX IF NOT EXISTS idx_ia_users_navegante ON ia_users(navegante_card_no);