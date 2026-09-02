CREATE TABLE contract_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) DEFAULT 'anonymous' NOT NULL UNIQUE,
    email VARCHAR(100),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    quota DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE contract_user IS 'Contract users';
COMMENT ON COLUMN contract_user.username IS 'Contract login name';

CREATE TABLE contract_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE contract_external (
    account_id VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
);

CREATE TABLE contract_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    city VARCHAR(50),
    CONSTRAINT fk_contract_address_user FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_group (
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    CONSTRAINT pk_contract_user_group PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_contract_ug_user FOREIGN KEY (user_id) REFERENCES contract_user(id),
    CONSTRAINT fk_contract_ug_group FOREIGN KEY (group_id) REFERENCES contract_group(id)
);

CREATE TABLE contract_user_profile (
    user_id BIGINT PRIMARY KEY,
    bio VARCHAR(200),
    CONSTRAINT fk_contract_user_profile FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_email (
    user_id BIGINT NOT NULL,
    email_address VARCHAR(100) NOT NULL,
    CONSTRAINT pk_contract_user_email PRIMARY KEY (user_id, email_address),
    CONSTRAINT fk_contract_user_email FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_phone (
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    phone_type VARCHAR(30),
    priority INTEGER,
    CONSTRAINT pk_contract_user_phone PRIMARY KEY (user_id, phone_number),
    CONSTRAINT fk_contract_user_phone FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_user_alias (
    username VARCHAR(50) NOT NULL,
    alias_value VARCHAR(100) NOT NULL,
    CONSTRAINT pk_contract_user_alias PRIMARY KEY (username, alias_value),
    CONSTRAINT fk_contract_user_alias FOREIGN KEY (username) REFERENCES contract_user(username)
);

CREATE TABLE contract_composite (
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    role_name VARCHAR(50),
    CONSTRAINT pk_contract_composite PRIMARY KEY (tenant_id, record_id)
);

CREATE TABLE contract_composite_tag (
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    tag_value VARCHAR(100) NOT NULL,
    CONSTRAINT pk_contract_composite_tag PRIMARY KEY (tenant_id, record_id, tag_value),
    CONSTRAINT fk_contract_ctag_parent FOREIGN KEY (tenant_id, record_id)
        REFERENCES contract_composite(tenant_id, record_id)
);

CREATE VIEW contract_user_view AS
SELECT id, username, email FROM contract_user;

INSERT INTO contract_user (username, email, active, quota)
VALUES ('alice', 'alice@example.com', TRUE, 10.50);
INSERT INTO contract_user (username, email, active, quota)
VALUES ('bob', NULL, FALSE, 20.00);
INSERT INTO contract_group (name) VALUES ('developers');
INSERT INTO contract_user_group (user_id, group_id) VALUES (1, 1);
INSERT INTO contract_address (user_id, city) VALUES (1, 'Bratislava');
INSERT INTO contract_composite (tenant_id, record_id, role_name) VALUES (1, 1, 'owner');
INSERT INTO contract_external (account_id, display_name) VALUES ('existing', 'Existing account');
