-- Schema for sync tests with updated_at and deleted_at columns
CREATE TABLE sync_user (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    updated_at BIGINT NOT NULL DEFAULT 0,
    deleted_at BIGINT
);

CREATE TABLE sync_team (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    updated_at BIGINT NOT NULL DEFAULT 0,
    deleted_at BIGINT
);

CREATE TABLE sync_audit (
    uid BIGINT NOT NULL,
    operation VARCHAR(20) NOT NULL,
    timestamp BIGINT NOT NULL
);
