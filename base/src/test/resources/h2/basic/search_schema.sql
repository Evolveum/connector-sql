-- Search Integration Test Schema (lowercase, unquoted for H2 MySQL mode)
DROP TABLE IF EXISTS projectmembership CASCADE;
DROP TABLE IF EXISTS useraddress CASCADE;
DROP TABLE IF EXISTS project CASCADE;
DROP TABLE IF EXISTS app_group CASCADE;
DROP TABLE IF EXISTS app_role CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;

CREATE TABLE app_user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP
);

CREATE TABLE app_group (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
);

CREATE TABLE app_role (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
);

CREATE TABLE project (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    created_at TIMESTAMP
);

CREATE TABLE useraddress (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    street VARCHAR(255),
    city VARCHAR(255),
    country VARCHAR(255),
    primary_flag BOOLEAN
);

CREATE TABLE projectmembership (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    project_id INT NOT NULL,
    role_id INT NOT NULL,
    joined_at TIMESTAMP
);

ALTER TABLE useraddress ADD CONSTRAINT fk_user_address_user
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

ALTER TABLE projectmembership ADD CONSTRAINT fk_membership_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

ALTER TABLE projectmembership ADD CONSTRAINT fk_membership_project
    FOREIGN KEY (project_id) REFERENCES project(id);

ALTER TABLE projectmembership ADD CONSTRAINT fk_membership_role
    FOREIGN KEY (role_id) REFERENCES app_role(id);
