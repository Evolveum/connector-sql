--
-- Copyright (c) 2026 Evolveum and contributors
--
-- This work is licensed under European Union Public License v1.2. See LICENSE file for details.
--

-- Sample data for H2 integration tests
-- Inserts data into the schema tables defined in schema.sql

INSERT INTO "User" (username, email, created_at) VALUES
    ('john.doe', 'john@example.com', CURRENT_TIMESTAMP()),
    ('jane.smith', 'jane@example.com', CURRENT_TIMESTAMP());

INSERT INTO "Group" (name, description) VALUES
    ('Developers', 'Software developers'),
    ('Admins', 'System administrators');

INSERT INTO "Role" (name, description) VALUES
    ('Owner', 'Project owner'),
    ('Member', 'Regular member'),
    ('Reviewer', 'Code reviewer');

INSERT INTO Project (name, description, created_at) VALUES
    ('Alpha', 'First project', CURRENT_TIMESTAMP()),
    ('Beta', 'Second project', CURRENT_TIMESTAMP());

INSERT INTO UserAddress (user_id, street, city, country, primary_flag) VALUES
    (1, '123 Main St', 'New York', 'US', true),
    (1, '456 Oak Ave', 'Boston', 'US', false),
    (2, '789 Elm St', 'San Francisco', 'US', true);

INSERT INTO ProjectMembership (user_id, project_id, role_id, joined_at) VALUES
    (1, 1, 1, CURRENT_TIMESTAMP()),
    (1, 2, 2, CURRENT_TIMESTAMP()),
    (2, 1, 3, CURRENT_TIMESTAMP());