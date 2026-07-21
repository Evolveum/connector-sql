-- Oracle Schema: Combined (Orgchart + Directory)
-- For integration tests on Oracle 23c Free (FREEPDB1)

-- Drop tables in reverse dependency order
BEGIN EXECUTE IMMEDIATE 'DROP TABLE orgchart_label'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE orgchart_node'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_job_watermark'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_xf_entitlement'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_membership'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_service'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_auth_domain'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_staff_origin_ref'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_institution_ref'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_archetype_ref'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE dir_status_ref'; EXCEPTION WHEN OTHERS THEN NULL; END;

-- ============================================
-- DIRECTORY SCHEMA (CUD/Directory)
-- ============================================

CREATE TABLE dir_status_ref (
    status_code    VARCHAR2(1) NOT NULL,
    status_meaning VARCHAR2(100) NOT NULL,
    CONSTRAINT pk_dir_status    PRIMARY KEY (status_code)
);

CREATE TABLE dir_archetype_ref (
    archetype_code        VARCHAR2(1) NOT NULL,
    archetype_description VARCHAR2(255) NOT NULL,
    CONSTRAINT pk_dir_archetype PRIMARY KEY (archetype_code)
);

CREATE TABLE dir_staff_origin_ref (
    origin_code       VARCHAR2(20) NOT NULL,
    origin_meaning    VARCHAR2(255) NOT NULL,
    CONSTRAINT pk_dir_origin       PRIMARY KEY (origin_code)
);

CREATE TABLE dir_institution_ref (
    origin_code     VARCHAR2(20) NOT NULL,
    institution_name VARCHAR2(255) NOT NULL,
    CONSTRAINT pk_dir_institution  PRIMARY KEY (origin_code)
);

CREATE TABLE dir_auth_domain (
    domain_id   NUMBER(10) NOT NULL,
    domain_name VARCHAR2(100) NOT NULL,
    CONSTRAINT pk_dir_domain    PRIMARY KEY (domain_id)
);

CREATE TABLE dir_account (
    account_id         VARCHAR2(8) NOT NULL,
    family_name        VARCHAR2(400) NOT NULL,
    given_name         VARCHAR2(400),
    email_address      VARCHAR2(320) NOT NULL,
    status_code        VARCHAR2(1) NOT NULL,
    archetype_code     VARCHAR2(1) NOT NULL,
    domain_id          NUMBER(10),
    external_person_id VARCHAR2(8),
    internal_id        VARCHAR2(60),
    created_at         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_dir_account    PRIMARY KEY (account_id),
    CONSTRAINT fk_acct_status         FOREIGN KEY (status_code)  REFERENCES dir_status_ref(status_code),
    CONSTRAINT fk_acct_archetype      FOREIGN KEY (archetype_code) REFERENCES dir_archetype_ref(archetype_code),
    CONSTRAINT fk_acct_domain         FOREIGN KEY (domain_id)  REFERENCES dir_auth_domain(domain_id)
);

CREATE INDEX idx_dir_acct_status ON dir_account(status_code);
CREATE INDEX idx_dir_acct_domain ON dir_account(domain_id);
CREATE INDEX idx_dir_acct_person ON dir_account(external_person_id);

CREATE TABLE dir_service (
    service_id        VARCHAR2(64) NOT NULL,
    service_full_name VARCHAR2(255) NOT NULL,
    is_privileged     VARCHAR2(1) DEFAULT 'N',
    domain_id         NUMBER(10),
    directorategeneral VARCHAR2(10),
    app_name          VARCHAR2(256),
    endpoint_time     NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_dir_service      PRIMARY KEY (service_id),
    CONSTRAINT fk_svc_domain       FOREIGN KEY (domain_id) REFERENCES dir_auth_domain(domain_id)
);

CREATE INDEX idx_dir_svc_time    ON dir_service(endpoint_time);

CREATE TABLE dir_membership (
    account_id        VARCHAR2(8) NOT NULL,
    service_id        VARCHAR2(64) NOT NULL,
    membership_expiry TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_dir_membership   PRIMARY KEY (account_id, service_id, membership_expiry),
    CONSTRAINT fk_mem_acct         FOREIGN KEY (account_id) REFERENCES dir_account(account_id),
    CONSTRAINT fk_mem_svc          FOREIGN KEY (service_id) REFERENCES dir_service(service_id)
);

CREATE TABLE dir_xf_entitlement (
    account_id    VARCHAR2(8) NOT NULL,
    is_entitled   VARCHAR2(1) NOT NULL,
    endpoint_time NUMBER(19) NOT NULL,
    CONSTRAINT pk_dir_xf       PRIMARY KEY (account_id),
    CONSTRAINT fk_xf_acct      FOREIGN KEY (account_id) REFERENCES dir_account(account_id)
);

CREATE INDEX idx_dir_xf_time ON dir_xf_entitlement(endpoint_time);

CREATE TABLE dir_job_watermark (
    job_name        VARCHAR2(30) NOT NULL,
    last_confirmed  TIMESTAMP(6),
    operator        VARCHAR2(50),
    note            VARCHAR2(500),
    processed_rows  NUMBER(10),
    updated_at      TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_dir_watermark PRIMARY KEY (job_name)
);

-- ============================================
-- ORGCHART SCHEMA (COMREF/Orgchart)
-- ============================================

CREATE TABLE orgchart_type_ref (
    type_ref_id  NUMBER(10) NOT NULL,
    type_code    VARCHAR2(20) NOT NULL,
    display_name VARCHAR2(255) NOT NULL,
    CONSTRAINT pk_orgchart_type_ref PRIMARY KEY (type_ref_id)
);

CREATE UNIQUE INDEX idx_orgchart_type ON orgchart_type_ref(type_code);

CREATE TABLE orgchart_node (
    unit_id        NUMBER(10) NOT NULL,
    unit_code      VARCHAR2(100) NOT NULL,
    parent_unit_id NUMBER(10),
    type_ref_id    NUMBER(10),
    hierarchy_level NUMBER(3) NOT NULL,
    display_order  NUMBER(10),
    created_date   TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_date   TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_orgchart_node        PRIMARY KEY (unit_id),
    CONSTRAINT fk_org_type             FOREIGN KEY (type_ref_id) REFERENCES orgchart_type_ref(type_ref_id),
    CONSTRAINT fk_org_parent           FOREIGN KEY (parent_unit_id) REFERENCES orgchart_node(unit_id)
);

CREATE INDEX idx_orgchart_node_code ON orgchart_node(unit_code);
CREATE INDEX idx_orgchart_node_level ON orgchart_node(hierarchy_level);

CREATE TABLE orgchart_label (
    unit_id      NUMBER(10) NOT NULL,
    language     VARCHAR2(10) NOT NULL,
    label_text   VARCHAR2(500) NOT NULL,
    CONSTRAINT pk_orgchart_label      PRIMARY KEY (unit_id, language),
    CONSTRAINT fk_org_node_label      FOREIGN KEY (unit_id) REFERENCES orgchart_node(unit_id)
);

-- ============================================
-- SEED DATA: DIRECTORY
-- ============================================

INSERT INTO dir_status_ref (status_code, status_meaning) VALUES ('a', 'Active');
INSERT INTO dir_status_ref (status_code, status_meaning) VALUES ('b', 'Blocked');
INSERT INTO dir_status_ref (status_code, status_meaning) VALUES ('d', 'Deleted');
INSERT INTO dir_status_ref (status_code, status_meaning) VALUES ('i', 'Inactive');
INSERT INTO dir_status_ref (status_code, status_meaning) VALUES ('s', 'Suspended');

INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('f', 'Full Staff');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('c', 'Contract Staff');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('a', 'Aux Staff');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('q', 'Temp Staff');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('i', 'Interinst Staff');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('m', 'Pensioner');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('x', 'External Inprem');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('e', 'External Generic');
INSERT INTO dir_archetype_ref (archetype_code, archetype_description) VALUES ('g', 'Guest');

INSERT INTO dir_staff_origin_ref (origin_code, origin_meaning) VALUES ('SIE', 'Headquarters');
INSERT INTO dir_staff_origin_ref (origin_code, origin_meaning) VALUES ('DEL', 'Delegation');
INSERT INTO dir_staff_origin_ref (origin_code, origin_meaning) VALUES ('REP', 'Representation');
INSERT INTO dir_staff_origin_ref (origin_code, origin_meaning) VALUES ('CCR', 'Joint Research');

INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('CORP', 'Acme Corporation');
INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('BANK', 'Global Bank Corp');
INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('TECH', 'Tech Solutions Inc');
INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('HEALTH', 'Health Services Ltd');
INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('RESEAUX', 'Networking Group');
INSERT INTO dir_institution_ref (origin_code, institution_name) VALUES ('INSUR', 'Insurance Partners');

INSERT INTO dir_auth_domain (domain_id, domain_name) VALUES (1001, 'example');
INSERT INTO dir_auth_domain (domain_id, domain_name) VALUES (2002, 'dev.example');

INSERT INTO dir_account (account_id, family_name, given_name, email_address, status_code, archetype_code, domain_id, external_person_id, created_at, updated_at)
VALUES ('USR00001', 'User1', 'User', 'user1@example.com', 'a', 'f', 1001, NULL, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO dir_account (account_id, family_name, given_name, email_address, status_code, archetype_code, domain_id, external_person_id, created_at, updated_at)
VALUES ('USR00002', 'User2', 'User', 'user2@example.com', 'a', 'c', 2002, NULL, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO dir_account (account_id, family_name, given_name, email_address, status_code, archetype_code, domain_id, external_person_id, created_at, updated_at)
VALUES ('USR00003', 'User3', 'User', 'user3@example.com', 'd', 'q', 1001, NULL, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO dir_account (account_id, family_name, given_name, email_address, status_code, archetype_code, domain_id, external_person_id, created_at, updated_at)
VALUES ('USR00004', 'User4', 'User', 'user4@example.com', 'i', 'f', 1001, NULL, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO dir_service (service_id, service_full_name, is_privileged, domain_id, endpoint_time) VALUES ('SVC_ADMIN', 'Administrators', 'Y', 1001, 1000);
INSERT INTO dir_service (service_id, service_full_name, is_privileged, domain_id, endpoint_time) VALUES ('SVC_DEV', 'Developers', 'N', 1001, 1000);

INSERT INTO dir_membership (account_id, service_id, membership_expiry, updated_at) VALUES ('USR00001', 'SVC_ADMIN', TIMESTAMP '3000-01-01 00:00:00', SYSTIMESTAMP);
INSERT INTO dir_membership (account_id, service_id, membership_expiry, updated_at) VALUES ('USR00001', 'SVC_DEV', TIMESTAMP '3000-01-01 00:00:00', SYSTIMESTAMP);

INSERT INTO dir_xf_entitlement (account_id, is_entitled, endpoint_time) VALUES ('USR00001', 'Y', 1000);

INSERT INTO dir_job_watermark (job_name, updated_at) VALUES ('dir_users', SYSTIMESTAMP);

-- ============================================
-- SEED DATA: ORGCHART
-- ============================================

INSERT INTO orgchart_type_ref (type_ref_id, type_code, display_name) VALUES (100, 'INST', 'Institution');
INSERT INTO orgchart_type_ref (type_ref_id, type_code, display_name) VALUES (200, 'DEPT', 'Department-General');
INSERT INTO orgchart_type_ref (type_ref_id, type_code, display_name) VALUES (250, 'UNIT', 'Unit');
INSERT INTO orgchart_type_ref (type_ref_id, type_code, display_name) VALUES (300, 'SECT', 'Sector');

INSERT INTO orgchart_node (unit_id, unit_code, parent_unit_id, type_ref_id, hierarchy_level, display_order, created_date, updated_date)
VALUES (1000, 'ORG.ROOT', NULL, 100, 1, 1, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO orgchart_node (unit_id, unit_code, parent_unit_id, type_ref_id, hierarchy_level, display_order, created_date, updated_date)
VALUES (2000, 'ORG.DEPT1', 1000, 200, 2, 1, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO orgchart_node (unit_id, unit_code, parent_unit_id, type_ref_id, hierarchy_level, display_order, created_date, updated_date)
VALUES (2100, 'ORG.DEPT2', 1000, 200, 2, 2, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO orgchart_node (unit_id, unit_code, parent_unit_id, type_ref_id, hierarchy_level, display_order, created_date, updated_date)
VALUES (3000, 'ORG.DEPT1.U01', 2000, 250, 3, 1, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO orgchart_node (unit_id, unit_code, parent_unit_id, type_ref_id, hierarchy_level, display_order, created_date, updated_date)
VALUES (3100, 'ORG.DEPT1.U02', 2000, 300, 3, 2, SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (1000, 'ENG', 'Organisation');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (1000, 'FRA', 'OrganisationFr');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (2000, 'ENG', 'Department1');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (2000, 'FRA', 'Department1Fr');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (2100, 'ENG', 'Department2');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (2100, 'FRA', 'Department2Fr');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (3000, 'ENG', 'Unit1');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (3000, 'FRA', 'Unit1Fr');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (3100, 'ENG', 'Unit2');
INSERT INTO orgchart_label (unit_id, language, label_text) VALUES (3100, 'FRA', 'Unit2Fr');
