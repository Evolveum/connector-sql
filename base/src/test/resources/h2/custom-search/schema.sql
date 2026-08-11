-- Test schema for custom search features
-- Extended from search_schema with legacy/status columns for where clause tests
DROP TABLE IF EXISTS extended_users CASCADE;
DROP TABLE IF EXISTS account_records CASCADE;

-- Standard users table with legacy/status columns for where clause filtering
CREATE TABLE extended_users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    legacy BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP
);

-- Account table for custom query tests (different from extended_users)
CREATE TABLE account_records (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP
);

-- Insert test data
INSERT INTO extended_users (username, email, status, legacy) VALUES
    ('user.active', 'active@test.com', 'active', FALSE),
    ('user.inactive', 'inactive@test.com', 'inactive', FALSE),
    ('user.deleted', 'deleted@test.com', 'deleted', FALSE),
    ('user.legacy', 'legacy@test.com', 'active', TRUE);

INSERT INTO account_records (username, email, status, active) VALUES
    ('acct.active', 'acct.active@test.com', 'active', TRUE),
    ('acct.suspended', 'acct.suspended@test.com', 'suspended', TRUE),
    ('acct.closed', 'acct.closed@test.com', 'closed', FALSE);
