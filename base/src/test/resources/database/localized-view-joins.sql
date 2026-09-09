-- Synthetic, source-scoped units with translated labels. Views expose no foreign keys.
CREATE TABLE contract_unit (
    tenant_key VARCHAR(20) NOT NULL,
    unit_key INTEGER NOT NULL,
    source_key INTEGER NOT NULL,
    kind_key INTEGER NOT NULL,
    PRIMARY KEY (tenant_key, unit_key)
);

-- Lookup uniqueness is deliberately not enforced: tests also exercise duplicate view rows.
CREATE TABLE contract_unit_type (
    tenant_key VARCHAR(20) NOT NULL,
    kind_key INTEGER NOT NULL,
    source_key INTEGER NOT NULL,
    type_name VARCHAR(80) NOT NULL
);

-- Labels belong to a unit and language, not to a source stream.
CREATE TABLE contract_unit_label (
    tenant_key VARCHAR(20) NOT NULL,
    unit_key INTEGER NOT NULL,
    locale_code VARCHAR(10) NOT NULL,
    label_text VARCHAR(80)
);

CREATE VIEW contract_unit_v AS
SELECT tenant_key, unit_key, source_key, kind_key FROM contract_unit;
CREATE VIEW contract_unit_type_v AS
SELECT tenant_key, kind_key, source_key, type_name FROM contract_unit_type;
CREATE VIEW contract_unit_label_v AS
SELECT tenant_key, unit_key, locale_code, label_text FROM contract_unit_label;

INSERT INTO contract_unit VALUES ('north', 1, 100, 10);
INSERT INTO contract_unit VALUES ('north', 2, 200, 10);
INSERT INTO contract_unit VALUES ('south', 1, 100, 10);
INSERT INTO contract_unit VALUES ('north', 3, 100, 20);
INSERT INTO contract_unit VALUES ('north', 4, 999, 10);
INSERT INTO contract_unit VALUES ('north', 5, 100, 10);
INSERT INTO contract_unit VALUES ('north', 6, 100, 10);

-- Each of the first four rows differs from another in just one join-key component.
INSERT INTO contract_unit_type VALUES ('north', 10, 100, 'Department');
INSERT INTO contract_unit_type VALUES ('north', 10, 200, 'Project');
INSERT INTO contract_unit_type VALUES ('south', 10, 100, 'Division');
INSERT INTO contract_unit_type VALUES ('north', 20, 100, 'Office');

INSERT INTO contract_unit_label VALUES ('north', 1, 'en', 'North team');
INSERT INTO contract_unit_label VALUES ('north', 1, 'fr', 'Equipe nord');
INSERT INTO contract_unit_label VALUES ('north', 1, 'de', 'Nordteam');
INSERT INTO contract_unit_label VALUES ('north', 1, 'es', 'Unused language');
INSERT INTO contract_unit_label VALUES ('north', 2, 'en', 'Project team');
INSERT INTO contract_unit_label VALUES ('south', 1, 'en', 'South team');
INSERT INTO contract_unit_label VALUES ('south', 1, 'fr', 'Equipe sud');
INSERT INTO contract_unit_label VALUES ('south', 1, 'de', 'Suedteam');
INSERT INTO contract_unit_label VALUES ('north', 3, 'en', 'Office team');
INSERT INTO contract_unit_label VALUES ('north', 4, 'en', 'Unknown type team');
INSERT INTO contract_unit_label VALUES ('north', 6, 'fr', 'Francais seulement');
INSERT INTO contract_unit_label VALUES ('north', 99, 'en', 'Orphan label');
