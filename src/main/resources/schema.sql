DROP TABLE IF EXISTS ingestion_exclusion_log CASCADE;
DROP TABLE IF EXISTS tenancy_record CASCADE;
DROP TABLE IF EXISTS unit CASCADE;
DROP TABLE IF EXISTS site CASCADE;

CREATE TABLE site (
    pnu               VARCHAR(19) PRIMARY KEY,
    jibun_address     VARCHAR(200) NOT NULL,
    road_address      VARCHAR(200),
    longitude         DECIMAL(10,7),
    latitude          DECIMAL(10,7),
    original_x        DECIMAL(18,9),
    original_y        DECIMAL(18,9),
    address_corrected BOOLEAN,
    local_gov_code    VARCHAR(10)
);

CREATE INDEX idx_site_jibun ON site(jibun_address);

CREATE TABLE unit (
    unit_id         VARCHAR(40) PRIMARY KEY,
    site_pnu        VARCHAR(19) NOT NULL REFERENCES site(pnu),
    label           VARCHAR(80) NOT NULL,
    location_source VARCHAR(20) NOT NULL DEFAULT 'license'
);

CREATE INDEX idx_unit_site ON unit(site_pnu);

CREATE TABLE tenancy_record (
    id              BIGINT PRIMARY KEY,
    unit_id         VARCHAR(40) NOT NULL REFERENCES unit(unit_id),
    license_no      VARCHAR(50),
    business_name   VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    sub_category    VARCHAR(50) NOT NULL,
    licensed_at     DATE NOT NULL,
    closed_at       DATE NULL,
    status          VARCHAR(10) NOT NULL,
    status_detail   VARCHAR(30),
    CONSTRAINT chk_date_order CHECK (closed_at IS NULL OR closed_at >= licensed_at)
);

CREATE INDEX idx_tenancy_unit ON tenancy_record(unit_id);
CREATE INDEX idx_tenancy_status ON tenancy_record(status);
CREATE INDEX idx_tenancy_licensed ON tenancy_record(licensed_at);

CREATE TABLE ingestion_exclusion_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id    BIGINT,
    reason_code  VARCHAR(40) NOT NULL,
    raw_snippet  VARCHAR(500),
    logged_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
