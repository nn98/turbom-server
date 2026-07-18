DROP TABLE IF EXISTS auction_schedule_entry CASCADE;
DROP TABLE IF EXISTS auction_case CASCADE;
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
    parsed_building_name   VARCHAR(200),
    parsed_floor           VARCHAR(20),
    parsed_unit_no         VARCHAR(20),
    parse_confidence       VARCHAR(10),
    parse_method           VARCHAR(20),
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

CREATE TABLE auction_case (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_number                VARCHAR(50) NOT NULL,
    item_number                INT NOT NULL,
    court                      VARCHAR(100),
    division_name              VARCHAR(100),
    property_type              VARCHAR(100),
    jibun_address              VARCHAR(300),
    appraisal_value_krw        DECIMAL(19,0),
    minimum_sale_price_krw     DECIMAL(19,0),
    bid_deposit_krw            DECIMAL(19,0),
    bidding_method             VARCHAR(50),
    sale_date                  VARCHAR(20),
    filed_date                 VARCHAR(20),
    auction_start_date         VARCHAR(20),
    claim_deadline             VARCHAR(20),
    claim_amount_krw           DECIMAL(19,0),
    appraisal_summary          VARCHAR(4000)
);

CREATE TABLE auction_schedule_entry (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    auction_case_id            BIGINT NOT NULL REFERENCES auction_case(id),
    schedule_date              VARCHAR(20),
    schedule_time              VARCHAR(20),
    schedule_type              VARCHAR(100),
    location                   VARCHAR(300),
    minimum_price_krw          DECIMAL(19,0),
    result                     VARCHAR(100)
);
