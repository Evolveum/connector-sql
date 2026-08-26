CREATE TABLE contract_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) DEFAULT 'anonymous' NOT NULL UNIQUE COMMENT 'Contract login name',
    email VARCHAR(100),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    quota DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_contract_user PRIMARY KEY (id)
) COMMENT = 'Contract users';

CREATE TABLE contract_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    CONSTRAINT pk_contract_group PRIMARY KEY (id)
);

CREATE TABLE contract_external (
    account_id VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
);

CREATE TABLE contract_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    city VARCHAR(50),
    CONSTRAINT pk_contract_address PRIMARY KEY (id),
    CONSTRAINT fk_contract_address_user FOREIGN KEY (user_id) REFERENCES contract_user(id)
);

CREATE TABLE contract_composite (
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    role_name VARCHAR(50),
    CONSTRAINT pk_contract_composite PRIMARY KEY (tenant_id, record_id)
);

CREATE VIEW contract_user_view AS
SELECT id, username, email FROM contract_user;

INSERT INTO contract_user (username, email, active, quota)
VALUES ('alice', 'alice@example.com', TRUE, 10.50);
INSERT INTO contract_user (username, email, active, quota)
VALUES ('bob', NULL, FALSE, 20.00);
INSERT INTO contract_group (name) VALUES ('developers');
INSERT INTO contract_address (user_id, city) VALUES (1, 'Bratislava');
INSERT INTO contract_composite (tenant_id, record_id, role_name) VALUES (1, 1, 'owner');
INSERT INTO contract_external (account_id, display_name) VALUES ('existing', 'Existing account');
