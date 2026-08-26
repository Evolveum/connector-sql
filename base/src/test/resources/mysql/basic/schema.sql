DROP VIEW IF EXISTS app_user_view;
DROP TABLE IF EXISTS membership;
DROP TABLE IF EXISTS app_group;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL DEFAULT 'anonymous' COMMENT 'Application login name',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_app_user PRIMARY KEY (id)
) COMMENT = 'Application users';

CREATE TABLE app_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_app_group PRIMARY KEY (id),
    CONSTRAINT uq_app_group_name UNIQUE (name)
);

CREATE TABLE membership (
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    CONSTRAINT pk_membership PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES app_group (id)
);

CREATE VIEW app_user_view AS
SELECT id, username, created_at FROM app_user;
