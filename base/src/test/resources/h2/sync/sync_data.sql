-- Initial data for sync tests
-- All timestamps start at 1000 so we can test incremental sync with tokens like 2000, 3000, etc.
INSERT INTO sync_user (id, name, email, updated_at) VALUES (1, 'Alice', 'alice@example.com', 1000);
INSERT INTO sync_user (id, name, email, updated_at) VALUES (2, 'Bob', 'bob@example.com', 1000);

-- Team that will be soft-deleted
INSERT INTO sync_team (id, name, description, updated_at) VALUES (1, 'Engineering', 'Main engineering team', 1000);

-- Audit table data
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (3, 'CREATE', 2000);
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (2, 'UPDATE', 2500);
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (999, 'DELETE', 3000);

INSERT INTO sync_audit (uid, operation, timestamp) VALUES (999, 'DELETE', 3000);
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (4, 'CREATE', 4000);

INSERT INTO sync_audit (uid, operation, timestamp) VALUES (2, 'UPDATE', 2500);
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (999, 'DELETE', 3000);
INSERT INTO sync_audit (uid, operation, timestamp) VALUES (4, 'CREATE', 4000);
