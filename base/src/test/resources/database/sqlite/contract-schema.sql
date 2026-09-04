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

CREATE TABLE contract_user_group (
    user_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL,
    CONSTRAINT pk_contract_user_group PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_contract_ug_user FOREIGN KEY (user_id) REFERENCES contract_user(id),
    CONSTRAINT fk_contract_ug_group FOREIGN KEY (group_id) REFERENCES contract_group(id)
);

CREATE TABLE contract_user_profile (
    user_id INTEGER PRIMARY KEY,
    bio TEXT,
    CONSTRAINT fk_contract_user_profile FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_email (
    user_id INTEGER NOT NULL,
    email_address TEXT NOT NULL,
    CONSTRAINT pk_contract_user_email PRIMARY KEY (user_id, email_address),
    CONSTRAINT fk_contract_user_email FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_phone (
    user_id INTEGER NOT NULL,
    phone_number TEXT NOT NULL,
    phone_type TEXT,
    priority INTEGER,
    CONSTRAINT pk_contract_user_phone PRIMARY KEY (user_id, phone_number),
    CONSTRAINT fk_contract_user_phone FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_alias (
    username TEXT NOT NULL,
    alias_value TEXT NOT NULL,
    CONSTRAINT pk_contract_user_alias PRIMARY KEY (username, alias_value),
    CONSTRAINT fk_contract_user_alias FOREIGN KEY (username) REFERENCES contract_user(username)
);

CREATE TABLE contract_composite (
    tenant_id INTEGER NOT NULL,
    record_id INTEGER NOT NULL,
    role_name TEXT,
    CONSTRAINT pk_contract_composite PRIMARY KEY (tenant_id, record_id)
);

CREATE TABLE contract_composite_tag (
    tenant_id INTEGER NOT NULL,
    record_id INTEGER NOT NULL,
    tag_value TEXT NOT NULL,
    CONSTRAINT pk_contract_composite_tag PRIMARY KEY (tenant_id, record_id, tag_value),
    CONSTRAINT fk_contract_ctag_parent FOREIGN KEY (tenant_id, record_id)
        REFERENCES contract_composite(tenant_id, record_id)
);

CREATE VIEW contract_user_view AS
SELECT id, username, email FROM contract_user;

INSERT INTO contract_user (username, email, active, quota)
VALUES ('alice', 'alice@example.com', 1, 10.50);
INSERT INTO contract_user (username, email, active, quota)
VALUES ('bob', NULL, 0, 20.00);
INSERT INTO contract_group (name) VALUES ('developers');
INSERT INTO contract_user_group (user_id, group_id) VALUES (1, 1);
INSERT INTO contract_address (user_id, city) VALUES (1, 'Bratislava');
INSERT INTO contract_composite (tenant_id, record_id, role_name) VALUES (1, 1, 'owner');
INSERT INTO contract_external (account_id, display_name) VALUES ('existing', 'Existing account');
