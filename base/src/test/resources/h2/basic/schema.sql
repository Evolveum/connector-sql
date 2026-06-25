-- H2 Basic Test Schema
-- Creates 6 tables with relationships for integration testing

-- Drop existing tables
DROP TABLE IF EXISTS ProjectMembership CASCADE;
DROP TABLE IF EXISTS UserAddress CASCADE;
DROP TABLE IF EXISTS Project CASCADE;
DROP TABLE IF EXISTS "Group" CASCADE;
DROP TABLE IF EXISTS "Role" CASCADE;
DROP TABLE IF EXISTS "User" CASCADE;

-- Create tables

CREATE TABLE "User" (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP
);

CREATE TABLE "Group" (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
);

CREATE TABLE "Role" (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
);

CREATE TABLE Project (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    created_at TIMESTAMP
);

CREATE TABLE UserAddress (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    street VARCHAR(255),
    city VARCHAR(255),
    country VARCHAR(255),
    primary_flag BOOLEAN
);

CREATE TABLE ProjectMembership (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    project_id INT NOT NULL,
    role_id INT NOT NULL,
    joined_at TIMESTAMP
);

-- Create foreign key constraints
ALTER TABLE UserAddress ADD CONSTRAINT fk_user_address_user
    FOREIGN KEY (user_id) REFERENCES "User"(id) ON DELETE CASCADE;

ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_user
    FOREIGN KEY (user_id) REFERENCES "User"(id);

ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_project
    FOREIGN KEY (project_id) REFERENCES Project(id);

ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_role
    FOREIGN KEY (role_id) REFERENCES "Role"(id);