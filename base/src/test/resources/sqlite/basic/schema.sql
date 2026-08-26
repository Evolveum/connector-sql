DROP VIEW IF EXISTS app_user_view;
DROP TABLE IF EXISTS membership;
DROP TABLE IF EXISTS app_group;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL DEFAULT 'anonymous',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_group (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE membership (
    user_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL,
    CONSTRAINT pk_membership PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES app_group (id)
);

CREATE VIEW app_user_view AS
SELECT id, username, created_at FROM app_user;
