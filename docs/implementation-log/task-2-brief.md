### Task 2: DB 스키마 + 시드 데이터

**Files:**
- Create: `server/src/main/resources/schema.sql`
- Create: `server/src/main/resources/data.sql`

**Interfaces:**
- Produces: H2에 `site`(6행) / `unit`(6행) / `tenancy_record`(8행) / `ingestion_exclusion_log`(0행) 테이블. 이후 모든 태스크가 이 시드를 전제로 테스트한다.

- [ ] **Step 1: schema.sql — `spec/schema.sql`을 그대로 복사**

```sql
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
```

- [ ] **Step 2: data.sql — 시드 6 site / 6 unit / 8 tenancy**

`spec/데이터_예시.csv`의 실제 6개 행(동물병원류)을 기반으로 하되, 두 자리(B, E)에는 이력 타임라인 데모용으로 이전 세입자 한 건씩을 손수 추가했다(사업장명에 "(seed 추가)" 등 표시 없이 자연스러운 이력으로 작성 — 실 데이터가 아니라 로컬 개발/데모 시드임은 `spec/CHANGELOG.md` 6차에 기록됨).

```sql
INSERT INTO site (pnu, jibun_address, road_address, longitude, latitude, original_x, original_y, address_corrected, local_gov_code) VALUES
('4113310100141900000', '경기도 성남시 중원구 성남동 4190번지 0호', '경기도 성남시 중원구 둔촌대로 79, 1층 (성남동)', 127.1279555, 37.4302075, 211258.126050314, 436467.042593183, NULL, '6410000'),
('4113110100100340000', '경기도 성남시 수정구 신흥동 34 수정빌딩', '경기도 성남시 수정구 수정로 273, 수정빌딩 1층 (신흥동)', 127.1456208, 37.4492216, 212818.475436898, 438579.588327304, TRUE, '3780000'),
('4113110100100300002', '경기도 성남시 수정구 신흥동 30-2 2층', '경기도 성남시 수정구 수정로 287, 2층 (신흥동)', 127.1464099, 37.4502874, 212888.115677533, 438697.984598845, TRUE, '3780000'),
('4113310300144030000', '경기도 성남시 중원구 금광동 4403', '경기도 성남시 중원구 산성대로 388-1, 1층 (금광동)', 127.1580659, 37.4465220, 213920.192888635, 438281.71088848, TRUE, '3780000'),
('4113110300100280001', '경기도 성남시 수정구 수진동 28-1 창도빌딩', '경기도 성남시 수정구 수정로 130, 창도빌딩 2층 (수진동)', 127.1348282, 37.4413688, 211864.688556887, 437706.627926558, TRUE, '3780000'),
('4113110800105090000', '경기도 성남시 수정구 창곡동 509 101호', '경기도 성남시 수정구 위례광장로 300, 101호 (창곡동)', 127.1427050, 37.4734110, 212556.476317272, 441263.937106913, NULL, '3780000');

INSERT INTO unit (unit_id, site_pnu, label, location_source) VALUES
('4113310100141900000-U1', '4113310100141900000', '단일 점포', 'license'),
('4113110100100340000-U1', '4113110100100340000', '단일 점포', 'license'),
('4113110100100300002-U1', '4113110100100300002', '단일 점포', 'license'),
('4113310300144030000-U1', '4113310300144030000', '단일 점포', 'license'),
('4113110300100280001-U1', '4113110300100280001', '단일 점포', 'license'),
('4113110800105090000-U1', '4113110800105090000', '단일 점포', 'license');

INSERT INTO tenancy_record (id, unit_id, license_no, business_name, category, sub_category, licensed_at, closed_at, status, status_detail) VALUES
(9612, '4113310100141900000-U1', '641000000520180001', '한국축산혁신협동조합', '동물', '도축업', '2018-03-14', '2024-03-13', '폐업', '폐업'),
(90001, '4113110100100340000-U1', '378000004920150001', '구정 동물미용실', '동물', '동물미용업', '2015-03-02', '2022-06-15', '폐업', '폐업'),
(9799, '4113110100100340000-U1', '378000004920230012', '동물병원 더 하임', '동물', '동물미용업', '2023-07-04', NULL, '영업', '정상'),
(9801, '4113110100100300002-U1', '378000004920230014', '스타동물의료센터', '동물', '동물미용업', '2023-07-28', NULL, '영업', '정상'),
(9802, '4113310300144030000-U1', '378000004920230015', '서울동물병원', '동물', '동물미용업', '2023-08-11', NULL, '영업', '정상'),
(90002, '4113110300100280001-U1', '378000004920160003', '성남동물카페', '동물', '동물카페', '2016-02-01', '2019-11-20', '폐업', '폐업'),
(9743, '4113110300100280001-U1', '378000004920200017', '미래동물의료센터 성남점', '동물', '동물미용업', '2020-04-16', '2023-12-28', '폐업', '폐업'),
(9919, '4113110800105090000-U1', '378000001020220006', '광장동물병원', '동물', '동물병원', '2022-08-31', '2023-09-30', '폐업', '폐업');
```

- [ ] **Step 3: 로딩 확인**

Run: `cd server && mvn -q spring-boot:run` (몇 초 대기 후 Ctrl+C로 중단해도 됨 — 목적은 예외 없이 뜨는지 확인)
Expected: 콘솔에 `Started NextstepApplication` 출력, `data.sql` 관련 SQL 에러 없음.

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/schema.sql src/main/resources/data.sql
git commit -m "feat: add H2 schema and dev seed data"
```

---

