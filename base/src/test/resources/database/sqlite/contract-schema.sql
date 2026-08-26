CREATE TABLE contract_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT DEFAULT 'anonymous' NOT NULL UNIQUE,
    email TEXT,
    active INTEGER DEFAULT 1 NOT NULL,
    quota NUMERIC,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE contract_group (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE contract_external (
    account_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL
);

CREATE TABLE contract_address (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    city TEXT,
    CONSTRAINT fk_contract_address_user FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_composite (
    tenant_id INTEGER NOT NULL,
    record_id INTEGER NOT NULL,
    role_name TEXT,
    CONSTRAINT pk_contract_composite PRIMARY KEY (tenant_id, record_id)
);

CREATE VIEW contract_user_view AS
SELECT id, username, email FROM contract_user;

INSERT INTO contract_user (username, email, active, quota)
VALUES ('alice', 'alice@example.com', 1, 10.50);
INSERT INTO contract_user (username, email, active, quota)
VALUES ('bob', NULL, 0, 20.00);
INSERT INTO contract_group (name) VALUES ('developers');
INSERT INTO contract_address (user_id, city) VALUES (1, 'Bratislava');
INSERT INTO contract_composite (tenant_id, record_id, role_name) VALUES (1, 1, 'owner');
INSERT INTO contract_external (account_id, display_name) VALUES ('existing', 'Existing account');
