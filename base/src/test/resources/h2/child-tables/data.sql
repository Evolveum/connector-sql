-- Child Table Detection Test Data

INSERT INTO users (id, username) VALUES (1, 'alice');
INSERT INTO users (id, username) VALUES (2, 'bob');

INSERT INTO user_profiles (user_id, bio) VALUES (1, 'Alice bio');
INSERT INTO user_profiles (user_id, bio) VALUES (2, 'Bob bio');

INSERT INTO user_emails (user_id, email) VALUES (1, 'alice@example.com');
INSERT INTO user_emails (user_id, email) VALUES (1, 'alice@work.com');
INSERT INTO user_emails (user_id, email) VALUES (2, 'bob@example.com');

-- Value table data (one phone per user)
INSERT INTO user_phones (user_id, phone_number, phone_type) VALUES (1, '555-0101', 'mobile');
INSERT INTO user_phones (user_id, phone_number, phone_type) VALUES (2, '555-0202', 'mobile');
INSERT INTO user_phones (user_id, phone_number, phone_type) VALUES (2, '555-0203', 'home');

INSERT INTO user_addresses (id, user_id, street, city) VALUES (1, 1, '123 Main St', 'Springfield');
INSERT INTO user_addresses (id, user_id, street, city) VALUES (2, 1, '456 Oak Ave', 'Shelbyville');
INSERT INTO user_addresses (id, user_id, street, city) VALUES (3, 2, '789 Pine Rd', 'Capital City');

INSERT INTO groups (id, name) VALUES (1, 'admins');
INSERT INTO groups (id, name) VALUES (2, 'users');
INSERT INTO groups (id, name) VALUES (3, 'developers');

INSERT INTO user_group_membership (user_id, group_id) VALUES (1, 1);
INSERT INTO user_group_membership (user_id, group_id) VALUES (1, 3);
INSERT INTO user_group_membership (user_id, group_id) VALUES (2, 2);
