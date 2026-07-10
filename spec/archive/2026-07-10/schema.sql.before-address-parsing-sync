DROP TABLE IF EXISTS ingestion_exclusion_log CASCADE;
DROP TABLE IF EXISTS tenancy_record CASCADE;
DROP TABLE IF EXISTS unit CASCADE;
DROP TABLE IF EXISTS site CASCADE;
DROP TABLE IF EXISTS licensed_business_record CASCADE;

CREATE TABLE licensed_business_record (
    id                     BIGINT PRIMARY KEY,
    pnu                    VARCHAR(19) NOT NULL,
    category               VARCHAR(50) NOT NULL,
    sub_category           VARCHAR(50) NOT NULL,
    license_no             VARCHAR(50) NOT NULL,
    business_name          VARCHAR(200) NOT NULL,
    business_type          VARCHAR(100),
    business_status        VARCHAR(20) NOT NULL,
    status_detail_code     VARCHAR(10),
    status_detail          VARCHAR(30),
    licensed_at            DATE NOT NULL,
    closed_at              DATE,
    road_address           VARCHAR(300),
    jibun_address          VARCHAR(300) NOT NULL,
    address_separated      BOOLEAN NOT NULL,
    address_corrected      BOOLEAN,
    local_gov_code         VARCHAR(10) NOT NULL,
    original_x             DECIMAL(18,9),
    original_y             DECIMAL(18,9),
    CONSTRAINT chk_license_date_order CHECK (closed_at IS NULL OR closed_at >= licensed_at)
);

CREATE INDEX idx_license_record_pnu ON licensed_business_record(pnu);
CREATE INDEX idx_license_record_jibun ON licensed_business_record(jibun_address);
CREATE INDEX idx_license_record_road ON licensed_business_record(road_address);
CREATE INDEX idx_license_record_status ON licensed_business_record(business_status);
CREATE INDEX idx_license_record_licensed ON licensed_business_record(licensed_at);
