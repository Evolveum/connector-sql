-- Child Table Detection Test Schema (lowercase, unquoted for H2 MySQL mode)
DROP TABLE IF EXISTS user_group_membership CASCADE;
DROP TABLE IF EXISTS user_addresses CASCADE;
DROP TABLE IF EXISTS user_emails CASCADE;
DROP TABLE IF EXISTS user_profiles CASCADE;
DROP TABLE IF EXISTS user_phones CASCADE;
DROP TABLE IF EXISTS groups CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE user_profiles (
    user_id INT PRIMARY KEY,
    bio VARCHAR(1024),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_emails (
    user_id INT,
    email VARCHAR(255),
    PRIMARY KEY (user_id, email),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Classic "value table": FK is part of PK along with value columns
-- Pattern: (user_id, phone_number) as composite PK, one phone per user
CREATE TABLE user_phones (
    user_id INT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    phone_type VARCHAR(20),
    PRIMARY KEY (user_id, phone_number),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_addresses (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    street VARCHAR(255),
    city VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE groups (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE user_group_membership (
    user_id INT,
    group_id INT,
    PRIMARY KEY (user_id, group_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (group_id) REFERENCES groups(id)
);
