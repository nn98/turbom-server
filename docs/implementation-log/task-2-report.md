# Task 2: DB 스키마 + 시드 데이터 — 완료 보고

## 상태
**DONE** — schema.sql, data.sql 작성 완료, 서버 기동 확인, SQL 에러 없음, git 커밋 완료.

## 작업 내용

### Step 1: schema.sql 작성
- 파일: `server/src/main/resources/schema.sql`
- 내용: 4개 테이블 DDL 및 인덱스
  - `site` (PNU 기반, 19컬럼)
  - `unit` (물건, site 참조)
  - `tenancy_record` (이력, 8행 시드)
  - `ingestion_exclusion_log` (비어있음, 0행)
- 제약사항: 날짜 순서 검증 (closed_at >= licensed_at)

### Step 2: data.sql 작성
- 파일: `server/src/main/resources/data.sql`
- 내용: 6 site + 6 unit + 8 tenancy_record
- 데이터 구성:
  - 동물 카테고리 6개 지번
  - 물건(unit)은 각 지번당 1개 (단일 점포)
  - 이력(tenancy_record):
    - B, E 지번: 폐업 + 현재 영업 이력 (타임라인 데모용)
    - 나머지: 단일 또는 폐업 이력

### Step 3: 서버 기동 테스트
**로그 발췌:**
```
2026-07-10T07:45:09.869+09:00  INFO 32872 --- [nextstep-server] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer 
 : Tomcat started on port 9999 (http) with context path '/'
2026-07-10T07:45:09.874+09:00  INFO 32872 --- [nextstep-server] [           main] com.nextstep.NextstepApplication        
 : Started NextstepApplication in 2.149 seconds (process running for 2.399)
```
- **결과:** ✓ `Started NextstepApplication` 확인
- **SQL 에러:** 없음 (schema 및 data.sql 정상 로드)
- **데이터:** 테이블 4개, 행 20개(site 6 + unit 6 + tenancy 8) 생성 확인

### Step 4: git 커밋
```
[main fe7af29] feat: add H2 schema and dev seed data
 2 files changed, 73 insertions(+)
 create mode 100644 src/main/resources/data.sql
 create mode 100644 src/main/resources/schema.sql
```

## 기술 노트
- H2 메모리 모드(테스트)로 검증한 후 application.yml은 원본 파일 모드 설정 유지
- `spring.sql.init.mode: always` 정상 작동 (매번 schema → data 순서로 로드)
- 4개 테이블 + 3개 인덱스 + 1개 제약조건 모두 정상 적재
- `.gitignore`에 `data/` 포함되어 있음 (H2 파일 데이터는 커밋되지 않음)

## 검증 항목
- [x] schema.sql 작성 (spec/schema.sql 정확 복사)
- [x] data.sql 작성 (브리프 SQL 정확 복사)
- [x] 서버 기동 성공 ("Started NextstepApplication")
- [x] SQL 문법 에러 없음
- [x] 시드 데이터 정상 로드 (site 6행, unit 6행, tenancy 8행)
- [x] git add + commit 완료

---

**작업 완료 시각:** 2026-07-10 07:45:09+09:00  
**커밋 해시:** fe7af29
