BEGIN;

CREATE SCHEMA IF NOT EXISTS amc;
SET search_path TO amc;

CREATE TABLE clearance_level (
                                 clearance_id        SMALLSERIAL PRIMARY KEY,
                                 code                VARCHAR(20) NOT NULL UNIQUE,
                                 name                VARCHAR(80) NOT NULL,
                                 rank                SMALLINT NOT NULL UNIQUE,
                                 description         VARCHAR(255)
);

CREATE TABLE department (
                            department_id       SERIAL PRIMARY KEY,
                            code                VARCHAR(20) NOT NULL UNIQUE,
                            name                VARCHAR(100) NOT NULL,
                            station             VARCHAR(50) NOT NULL,
                            active              BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE crew_member (
                             crew_member_id      BIGSERIAL PRIMARY KEY,
                             external_uid        VARCHAR(64) NOT NULL UNIQUE,
                             given_name          VARCHAR(80) NOT NULL,
                             family_name         VARCHAR(80) NOT NULL,
                             display_name        VARCHAR(160) NOT NULL,
                             email               VARCHAR(255) UNIQUE,
                             employee_number     VARCHAR(20) UNIQUE,
                             status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                             clearance_id        SMALLINT REFERENCES clearance_level(clearance_id),
                             department_id       INTEGER REFERENCES department(department_id),
                             home_station        VARCHAR(50),
                             emergency_contact   VARCHAR(255),
                             hire_date           DATE,
                             termination_date    DATE,
                             created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             version             INTEGER NOT NULL DEFAULT 1,
                             CONSTRAINT crew_member_status_chk
                                 CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LEAVE', 'TERMINATED'))
);

CREATE TABLE crew_role (
                           crew_role_id        BIGSERIAL PRIMARY KEY,
                           external_uid        VARCHAR(64) NOT NULL UNIQUE,
                           name                VARCHAR(120) NOT NULL,
                           description         VARCHAR(500),
                           role_type           VARCHAR(30) NOT NULL DEFAULT 'OPERATIONAL',
                           station             VARCHAR(50),
                           requires_clearance  SMALLINT REFERENCES clearance_level(clearance_id),
                           active              BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           version             INTEGER NOT NULL DEFAULT 1,
                           CONSTRAINT crew_role_type_chk
                               CHECK (role_type IN (
                                                    'OPERATIONAL',
                                                    'SECURITY',
                                                    'MEDICAL',
                                                    'ENGINEERING',
                                                    'COMMAND',
                                                    'TRAINING',
                                                    'EXTERNAL'
                                   ))
);

CREATE TABLE crew_member_role (
                                  crew_member_id      BIGINT NOT NULL
                                      REFERENCES crew_member(crew_member_id) ON DELETE CASCADE,
                                  crew_role_id        BIGINT NOT NULL
                                      REFERENCES crew_role(crew_role_id) ON DELETE CASCADE,
                                  assigned_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  assigned_by         VARCHAR(80),
                                  assignment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                  expires_at          TIMESTAMPTZ,
                                  PRIMARY KEY (crew_member_id, crew_role_id),
                                  CONSTRAINT crew_member_role_status_chk
                                      CHECK (assignment_status IN ('ACTIVE', 'PENDING', 'EXPIRED'))
);

CREATE TABLE crew_member_audit (
                                   audit_id            BIGSERIAL PRIMARY KEY,
                                   crew_member_id      BIGINT NOT NULL
                                       REFERENCES crew_member(crew_member_id) ON DELETE CASCADE,
                                   changed_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   change_type         VARCHAR(20) NOT NULL,
                                   changed_by          VARCHAR(80),
                                   details             JSONB,
                                   CONSTRAINT crew_member_audit_type_chk
                                       CHECK (change_type IN (
                                                              'CREATE',
                                                              'UPDATE',
                                                              'DELETE',
                                                              'ROLE_CHANGE'
                                           ))
);

CREATE INDEX idx_crew_member_status
    ON crew_member(status);

CREATE INDEX idx_crew_member_department
    ON crew_member(department_id);

CREATE INDEX idx_crew_member_display_name
    ON crew_member(display_name);

CREATE INDEX idx_crew_role_type
    ON crew_role(role_type);

CREATE INDEX idx_crew_role_station
    ON crew_role(station);

CREATE INDEX idx_member_role_role
    ON crew_member_role(crew_role_id);

CREATE INDEX idx_member_role_status
    ON crew_member_role(assignment_status);

INSERT INTO clearance_level (code, name, rank, description) VALUES
                                                                ('C1', 'Surface Access', 1, 'Access to surface facilities'),
                                                                ('C2', 'Tunnel Access', 2, 'Access to pressurized tunnels'),
                                                                ('C3', 'Reactor Access', 3, 'Supervised reactor-section access'),
                                                                ('C4', 'Command Access', 4, 'Command systems and restricted records');

INSERT INTO department (code, name, station, active) VALUES
                                                         ('OPS', 'Mining Operations', 'Ceres Base', TRUE),
                                                         ('ENG', 'Engineering', 'Ceres Base', TRUE),
                                                         ('MED', 'Medical', 'Ceres Base', TRUE),
                                                         ('SEC', 'Security', 'Ceres Base', TRUE),
                                                         ('SCI', 'Science and Survey', 'Europa Orbital', TRUE);

INSERT INTO crew_member (
    external_uid, given_name, family_name, display_name, email,
    employee_number, status, clearance_id, department_id, home_station,
    emergency_contact, hire_date
) VALUES
      ('crew-1001', 'Mira', 'Voss', 'Mira Voss',
       'mira.voss@amc.example', 'AMC-1001', 'ACTIVE', 4, 4,
       'Ceres Base', '+421 900 000 001', DATE '2038-03-01'),

      ('crew-1002', 'Tomas', 'Radek', 'Tomas Radek',
       'tomas.radek@amc.example', 'AMC-1002', 'ACTIVE', 3, 2,
       'Ceres Base', '+421 900 000 002', DATE '2039-07-15'),

      ('crew-1003', 'Elena', 'Kovac', 'Elena Kovac',
       'elena.kovac@amc.example', 'AMC-1003', 'ACTIVE', 2, 3,
       'Ceres Base', '+421 900 000 003', DATE '2040-01-10'),

      ('crew-1004', 'Dmitri', 'Orlov', 'Dmitri Orlov',
       'dmitri.orlov@amc.example', 'AMC-1004', 'LEAVE', 1, 1,
       'Ceres Base', '+421 900 000 004', DATE '2041-05-20'),

      ('crew-1005', 'Sofia', 'Nakamura', 'Sofia Nakamura',
       'sofia.nakamura@amc.example', 'AMC-1005', 'ACTIVE', 3, 5,
       'Europa Orbital', '+421 900 000 005', DATE '2037-11-02'),

      ('crew-1006', 'Jonas', 'Weber', 'Jonas Weber',
       'jonas.weber@amc.example', 'AMC-1006', 'TERMINATED', 1, 1,
       'Ceres Base', '+421 900 000 006', DATE '2036-02-14');

INSERT INTO crew_role (
    external_uid, name, description, role_type, station,
    requires_clearance, active
) VALUES
      ('role-2001', 'Mining Crew Alpha',
       'Primary excavation and haulage team',
       'OPERATIONAL', 'Ceres Base', 2, TRUE),

      ('role-2002', 'Reactor Technicians',
       'Reactor maintenance and monitoring',
       'ENGINEERING', 'Ceres Base', 3, TRUE),

      ('role-2003', 'Medical Response Team',
       'Emergency medical response',
       'MEDICAL', 'Ceres Base', 2, TRUE),

      ('role-2004', 'Station Security',
       'Access control and incident response',
       'SECURITY', 'Ceres Base', 3, TRUE),

      ('role-2005', 'Survey Analysts',
       'Asteroid survey and data analysis',
       'OPERATIONAL', 'Europa Orbital', 2, TRUE),

      ('role-2006', 'External Contractors',
       'Contractors with limited access',
       'EXTERNAL', NULL, 1, TRUE);

INSERT INTO crew_member_role (
    crew_member_id, crew_role_id, assigned_by, assignment_status
)
SELECT
    cm.crew_member_id,
    cr.crew_role_id,
    'system-bootstrap',
    'ACTIVE'
FROM crew_member cm
         JOIN crew_role cr
              ON (cm.external_uid, cr.external_uid) IN (
                                                        ('crew-1001', 'role-2004'),
                                                        ('crew-1002', 'role-2002'),
                                                        ('crew-1003', 'role-2003'),
                                                        ('crew-1004', 'role-2001'),
                                                        ('crew-1005', 'role-2005'),
                                                        ('crew-1001', 'role-2006')
                  );

INSERT INTO crew_member_audit (
    crew_member_id, change_type, changed_by, details
)
SELECT
    crew_member_id,
    'CREATE',
    'system-bootstrap',
    jsonb_build_object('source', 'training seed data')
FROM crew_member;

COMMIT;