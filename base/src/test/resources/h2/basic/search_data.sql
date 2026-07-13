-- Data for SqlSearchOperationIntegrationTest
INSERT INTO app_user (username, email, created_at) VALUES
    ('john.doe', 'john@example.com', CURRENT_TIMESTAMP()),
    ('jane.smith', 'jane@example.com', CURRENT_TIMESTAMP());

INSERT INTO app_group (name, description) VALUES
    ('Developers', 'Software developers'),
    ('Admins', 'System administrators');

INSERT INTO app_role (name, description) VALUES
    ('Owner', 'Project owner'),
    ('Member', 'Regular member'),
    ('Reviewer', 'Code reviewer');

INSERT INTO project (name, description, created_at) VALUES
    ('Alpha', 'First project', CURRENT_TIMESTAMP()),
    ('Beta', 'Second project', CURRENT_TIMESTAMP());

INSERT INTO useraddress (user_id, street, city, country, primary_flag) VALUES
    (1, '123 Main St', 'New York', 'US', true),
    (1, '456 Oak Ave', 'Boston', 'US', false),
    (2, '789 Elm St', 'San Francisco', 'US', true);

INSERT INTO projectmembership (user_id, project_id, role_id, joined_at) VALUES
    (1, 1, 1, CURRENT_TIMESTAMP()),
    (1, 2, 2, CURRENT_TIMESTAMP()),
    (2, 1, 3, CURRENT_TIMESTAMP());
