-- Marion DMV — Out-of-State Title Transfer Assistant
-- Seed data for systems-of-record tables
-- All vehicles, states, and persons are fictional.
-- Idempotent: truncates then re-inserts. Run with: psql -U marion -d mariondmv -f seed.sql

-- ─────────────────────────────────────────────────────────────────────────────
-- RESET (safe to re-run)
-- ─────────────────────────────────────────────────────────────────────────────

DROP TABLE IF EXISTS inspection_stations CASCADE;
DROP TABLE IF EXISTS marion_counties CASCADE;
DROP TABLE IF EXISTS fee_schedule CASCADE;
DROP TABLE IF EXISTS tax_reciprocity CASCADE;
DROP TABLE IF EXISTS vehicles CASCADE;

-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: vehicles (title and lien database)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS vehicles (
    vin               VARCHAR(17) PRIMARY KEY,
    origin_state      VARCHAR(50) NOT NULL,
    title_form        VARCHAR(10) NOT NULL,   -- PAPER | ELT | MIXED
    lien_status       VARCHAR(20) NOT NULL,   -- NONE | RELEASED | ACTIVE
    lienholder_name   VARCHAR(100),
    lien_date         DATE,
    brand             VARCHAR(50),            -- NULL or brand term as on origin title
    make              VARCHAR(50),
    model             VARCHAR(50),
    model_year        INT,
    body_type         VARCHAR(30),
    gvwr_lbs          INT,
    odometer          INT,                    -- NULL = missing field (edge case)
    insurance_expiry  DATE,                   -- stale = edge case
    notes             VARCHAR(500)
);

-- 10 records designed to exercise each branching axis

INSERT INTO vehicles VALUES
-- 1. Verdana ELT — clean, no lien — happy path for ELT (A2 scenario)
('1VRD0000001000001', 'Verdana', 'ELT', 'NONE', NULL, NULL,
 NULL, 'Honda', 'Accord', 2019, 'Sedan', 3200, 52400,
 '2027-06-01', 'ELT clean happy path — Verdana'),

-- 2. Verdana ELT — active lien → exception (F2 scenario)
('1VRD0000001000002', 'Verdana', 'ELT', 'ACTIVE', 'First National Lending', '2021-09-15',
 NULL, 'Toyota', 'Camry', 2020, 'Sedan', 3400, 38100,
 '2026-12-01', 'ELT active lien — triggers supervisor referral'),

-- 2b. Verdana ELT — no lien — "Rebuilt" brand → exception (B2 eval scenario)
('1VRD0000001000003', 'Verdana', 'ELT', 'NONE', NULL, NULL,
 'Rebuilt', 'Mazda', 'CX-5', 2018, 'SUV', 3900, 44200,
 '2027-05-15', 'Verdana Rebuilt brand (= Marion Rebuilt) — branded title supervisor referral (B2 eval)'),

-- 3. Crestwood paper — clean, no lien — base happy path (A1 scenario)
('1CST0000001000001', 'Crestwood', 'PAPER', 'NONE', NULL, NULL,
 NULL, 'Ford', 'F-150', 2018, 'Pickup', 5500, 71200,
 '2027-03-15', 'Paper clean happy path — Crestwood purchase'),

-- 4. Crestwood paper — released lien — released lien path
('1CST0000001000002', 'Crestwood', 'PAPER', 'RELEASED', 'Crestwood Community Bank', '2019-03-01',
 NULL, 'Chevrolet', 'Equinox', 2017, 'SUV', 4100, 88900,
 '2027-01-20', 'Paper released lien — Crestwood, lien release on title face'),

-- 5. Crestwood paper — active lien → exception (F1 scenario — "Midwest Auto Finance")
('1CST0000001000003', 'Crestwood', 'PAPER', 'ACTIVE', 'Midwest Auto Finance', '2022-04-01',
 NULL, 'Nissan', 'Altima', 2021, 'Sedan', 3200, 24600,
 '2027-08-01', 'Active lien — paper — triggers supervisor referral (F1 eval scenario)'),

-- 6. Crestwood paper — no lien — Reconstructed brand → exception
('1CST0000001000004', 'Crestwood', 'PAPER', 'NONE', NULL, NULL,
 'Reconstructed', 'Dodge', 'Charger', 2016, 'Sedan', 4000, 104500,
 '2027-05-01', 'Recognized brand Reconstructed — triggers supervisor referral'),

-- 7. Halloway paper — no lien — "Rebuilt" brand (= Marion Reconstructed) → exception (F3 adjacent)
('1HAL0000001000001', 'Halloway', 'PAPER', 'NONE', NULL, NULL,
 'Rebuilt', 'Jeep', 'Wrangler', 2015, 'SUV', 4500, 118000,
 '2027-02-28', 'Halloway Rebuilt (= Marion Reconstructed) — equivalent brand exception'),

-- 8. Halloway paper — no lien — clean title, 2008 model year, Marion County
-- Tests: no Halloway emissions program + Marion emissions check (2026-2008=18 < 25 → emissions required if metro)
-- odometer is NULL (edge case — missing field)
('1HAL0000001000002', 'Halloway', 'PAPER', 'NONE', NULL, NULL,
 NULL, 'Toyota', 'Tacoma', 2008, 'Pickup', 4800, NULL,
 '2027-04-15', 'No Halloway emissions; Marion emissions check; odometer NULL (edge case)'),

-- 9. Pembrook paper — no lien — clean title → no reciprocity tax scenario
-- insurance_expiry is stale (edge case — tests tool error path)
('1PMB0000001000001', 'Pembrook', 'PAPER', 'NONE', NULL, NULL,
 NULL, 'Hyundai', 'Sonata', 2019, 'Sedan', 3300, 41800,
 '2024-11-30', 'No reciprocity — full Marion tax; insurance stale (edge case)'),

-- 10. Pembrook paper — no lien — "Salvage Rebuilt" compound brand → exception
('1PMB0000001000002', 'Pembrook', 'PAPER', 'NONE', NULL, NULL,
 'Salvage Rebuilt', 'BMW', '3 Series', 2017, 'Sedan', 3700, 62300,
 '2027-07-01', 'Pembrook compound brand Salvage Rebuilt (= Marion Rebuilt single brand) — exception');


-- ─────────────────────────────────────────────────────────────────────────────
-- VIN that returns NOT FOUND (used to test negative tool path)
-- Not inserted as a row — absence in the table IS the test.
-- VIN: 1VRD9999999999999
-- ─────────────────────────────────────────────────────────────────────────────


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: tax_reciprocity
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS tax_reciprocity (
    origin_state      VARCHAR(50) PRIMARY KEY,
    has_agreement     BOOLEAN NOT NULL,
    origin_rate_pct   NUMERIC(5,2),   -- NULL if no agreement
    notes             VARCHAR(300)
);

INSERT INTO tax_reciprocity VALUES
('Verdana',   TRUE,  5.00, 'Credit = min(Verdana tax paid, Marion tax owed). Marion rate 5.5%.'),
('Crestwood', TRUE,  6.00, 'Credit = min(Crestwood tax paid, Marion tax owed). Often $0 additional due.'),
('Halloway',  TRUE,  4.50, 'Credit = min(Halloway tax paid, Marion tax owed). Halloway rate lower than Marion.'),
('Pembrook',  FALSE, NULL, 'NO RECIPROCITY AGREEMENT. Full Marion tax (5.5%) owed regardless of Pembrook tax paid.');


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: fee_schedule
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS fee_schedule (
    fee_code          VARCHAR(50) PRIMARY KEY,
    description       VARCHAR(200) NOT NULL,
    amount            NUMERIC(8,2) NOT NULL,
    collected_by      VARCHAR(50) NOT NULL,   -- DMV | TESTING_STATION
    notes             VARCHAR(300)
);

INSERT INTO fee_schedule VALUES
('TITLE_TRANSFER',   'Out-of-state title transfer application (Form TR-1)',  25.00, 'DMV',            'Effective 2023-01-01. Prior fee: $20.00.'),
('VIN_INSPECTION',   'VIN inspection fee (Form TR-2)',                        15.00, 'DMV',            'Effective 2023-01-01. Prior fee: $10.00. Always due.'),
('EMISSIONS',        'Emissions test fee (Form EMIT-1)',                      35.00, 'TESTING_STATION','Paid to testing station. Due only for metro county + model year < 25 yrs.'),
('REG_LIGHT',        'Base registration — passenger vehicle <= 5,000 lbs',   45.00, 'DMV',            'Effective 2023-01-01. Prior fee: $38.00.'),
('REG_HEAVY',        'Base registration — passenger vehicle 5,001-8,500 lbs',65.00, 'DMV',            'Effective 2023-01-01. Prior fee: $55.00.'),
('LIEN_RELEASE',     'Lien release processing (Form TR-3)',                    5.00, 'DMV',            'Only when TR-3 used (Marion DMV lender system). Not for endorsement or letter releases.'),
('EXCEPTION_REVIEW', 'Supervisor exception referral (Form TR-10)',             0.00, 'DMV',            'No fee to applicant.'),
('DUPLICATE_TITLE',  'Duplicate Marion title (after issuance)',               10.00, 'DMV',            'Not part of initial transfer; reference only.');


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: inspection_stations
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS inspection_stations (
    station_id        SERIAL PRIMARY KEY,
    county            VARCHAR(50) NOT NULL,
    county_type       VARCHAR(10) NOT NULL,   -- METRO | RURAL
    station_name      VARCHAR(100),
    address           VARCHAR(200),
    inspection_types  VARCHAR(100),           -- VIN | EMISSIONS | BOTH
    phone             VARCHAR(20),
    notes             VARCHAR(300)
);

INSERT INTO inspection_stations (county, county_type, station_name, address, inspection_types, phone, notes) VALUES
-- Marion County (metro — emissions required)
('Marion County',    'METRO', 'Marion Central Inspection',    '1200 State Route 9, Marion City, MN 10001', 'BOTH',      '555-100-0001', 'Accepts walk-ins and appointments. Open M-F 8am-5pm.'),
('Marion County',    'METRO', 'Eastside VIN and Emissions',   '847 Industrial Blvd, Marion City, MN 10003', 'BOTH',     '555-100-0002', 'Appointment preferred. Saturdays 9am-1pm.'),
-- Riverside County (metro — emissions required)
('Riverside County', 'METRO', 'Riverside DMV Annex',          '33 County Road 7, Riverside, MN 10210',      'BOTH',     '555-200-0001', 'State-operated facility.'),
('Riverside County', 'METRO', 'Quick VIN Services LLC',       '500 Commerce Park, Riverside, MN 10215',     'VIN',      '555-200-0002', 'VIN inspection only; emissions must be done elsewhere.'),
-- Capital County (metro — emissions required)
('Capital County',   'METRO', 'Capital State Vehicle Center', '2 Government Plaza, Capital City, MN 10500', 'BOTH',     '555-300-0001', 'State facility. Appointment required.'),
-- Dunmore County (rural — exempt)
('Dunmore County',   'RURAL', NULL, NULL, NULL, NULL,          'No authorized emissions stations in Dunmore County — rural, exempt. VIN inspections by licensed inspectors or law enforcement only.'),
-- Alderton County (rural — exempt)
('Alderton County',  'RURAL', NULL, NULL, NULL, NULL,          'No authorized emissions stations in Alderton County — rural, exempt.');


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: marion_counties (reference — county type lookup)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS marion_counties (
    county_name       VARCHAR(50) PRIMARY KEY,
    county_type       VARCHAR(10) NOT NULL,
    emissions_required BOOLEAN NOT NULL,
    notes             VARCHAR(200)
);

INSERT INTO marion_counties VALUES
('Marion County',    'METRO',  TRUE,  'Admin. Rule 2.4 § 2.4.2 — metro, emissions required for qualifying vehicles.'),
('Riverside County', 'METRO',  TRUE,  'Admin. Rule 2.4 § 2.4.2 — metro, emissions required for qualifying vehicles.'),
('Capital County',   'METRO',  TRUE,  'Admin. Rule 2.4 § 2.4.2 — metro, emissions required for qualifying vehicles.'),
('Dunmore County',   'RURAL',  FALSE, 'Rural county — all vehicles exempt from emissions testing.'),
('Alderton County',  'RURAL',  FALSE, 'Rural county — all vehicles exempt from emissions testing.'),
('Fallkirk County',  'RURAL',  FALSE, 'Rural county — all vehicles exempt from emissions testing.'),
('Corville County',  'RURAL',  FALSE, 'Rural county — all vehicles exempt from emissions testing.');
