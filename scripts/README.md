# scripts/ — 원본 인허가 CSV → 서비스용 SQL 시드 변환

Java 앱 런타임과 무관한 오프라인 데이터 준비 도구. 정부 공공데이터(지방행정 인허가데이터개방
"LOCALDATA 표준" 계열 CSV)를 읽어 `licensed_business_record` 테이블용 SQL INSERT 청크를
생성해 `src/main/resources/data/`에 추가한다.

## 파일 구성

| 파일 | 역할 |
|---|---|
| `legaldong_codes.csv` | 전국 법정동코드 참조 테이블(코드, 명칭, 존재/폐지 여부). 45,958행 |
| `jibun_pnu.py` | 지번주소 문자열 → 19자리 PNU 파싱 순수 함수 |
| `parse_licensed_records.py` | CSV → SQL 청크 오케스트레이션(실행 진입점) |
| `test_jibun_pnu.py`, `test_parse_licensed_records.py` | pytest |

## 실행 방법

```bash
cd server/scripts
python3 parse_licensed_records.py <원본 CSV 경로> <output_dir> <start_id>
```

- `output_dir`은 보통 `../src/main/resources/data`(이미 있는 청크들과 이어붙임)
- `start_id`는 새 레코드의 첫 `id` — 기존 최대 id 다음 값을 직접 계산해서 넣어야 함(자동 계산 안 함).
  ```bash
  grep -ohE "^\([0-9]+," src/main/resources/data/*.sql | grep -oE "[0-9]+" | sort -n | tail -1
  ```
  로 현재 최대 id를 확인하고 +1 해서 넘긴다.

실행하면 표준출력에 생성된 레코드 수와 스킵 사유별 카운트가 찍힌다:

```
생성된 레코드: 7518
다음 시작 id: 109607
스킵 사유별 카운트:
  UNPARSEABLE_OR_DONG_NOT_FOUND: 728
  EMPTY: 301
```

**스킵 비율이 30%를 넘으면 뭔가 이상한 것** — 파서 로직이나 원본 컬럼 구조를 다시 확인할 것(과거
실측 기준 정상 범위는 한 자릿수~10%대).

## 원본 CSV가 갖춰야 할 조건

1. **파일명이 `대분류_소분류.csv` 또는 `대분류_소분류_지역.csv`** — 지역 세그먼트는 있어도 없어도
   됨(전국판 원본은 보통 지역 세그먼트가 없음). 예: `기타_담배소매업_경기성남시.csv`,
   `식품_일반음식점.csv` 둘 다 허용.
2. **인코딩은 cp949 고정** — 지금까지 다룬 모든 원본이 이 인코딩이었음. UTF-8로 오는 원본이
   생기면 `parse_file()`의 `open(csv_path, encoding="cp949")`를 고쳐야 함(현재 하드코딩).
3. **필수 컬럼**(정확히 이 이름으로 존재해야 함): `개방자치단체코드`, `관리번호`, `사업장명`,
   `영업상태명`, `상세영업상태코드`, `상세영업상태명`, `인허가일자`, `폐업일자`, `도로명주소`,
   `지번주소`, `좌표정보(X)`, `좌표정보(Y)`. 이 이름들은 지금까지 확인한 모든 LOCALDATA 계열
   원본에서 동일했음(컬럼 개수·순서·부가 컬럼은 파일마다 달라도 이 12개 이름은 항상 같았음).
4. **`business_status`는 원본의 `영업상태명` 값을 그대로 씀** — 이미 5버킷(영업/정상, 폐업, 휴업,
   취소/말소/만료/정지/중지, 제외/삭제/전출) 형식으로 채워져 있다고 가정. 파일마다 매핑 테이블을
   새로 만들 필요 없음(지금까지 확인한 모든 원본에서 이미 이 형식이었음).

컬럼 이름이 다르거나(3번 조건 위반) 인코딩이 다르면(2번) 이 스크립트를 그대로 못 씀 — 새 원본
포맷마다 필요한 만큼만 고쳐 쓸 것(범용 스키마 감지는 의도적으로 안 만듦, YAGNI).

## 처리 파이프라인 (스킵 체인 순서 = 코드 순서 그대로)

1. **성남시 사전 필터** — `개방자치단체코드 == "3780000"`이 아니면 즉시 스킵(`NOT_SEONGNAM`).
   전국판 원본(예: 230만 행짜리 파일)의 99% 이상이 여기서 걸러짐. 이 프로젝트가 성남시 한정이라
   하드코딩(`SEONGNAM_GOV_CODE`) — 다른 지자체 지원이 필요해지면 파일명의 지역 세그먼트에서
   코드를 유도하도록 확장해야 함.
2. **관리번호 중복 제거** — 새 행의 `관리번호`가 `output_dir` 안에 이미 있는 SQL 청크들의
   license_no 중 하나와 같으면 스킵(`DUPLICATE_LICENSE_NO`). 정부 공공데이터는 같은 데이터를
   시점만 다르게 재배포하는 경우가 흔해서(예: 660MB 전국판이 이미 적재된 10,211건과 관리번호
   체계가 동일했던 사례), 새 원본을 돌리기 전에 반드시 필요한 방어.
3. **지번주소 → PNU 계산**(`jibun_pnu.parse_pnu`) — 지번주소가 비어있으면 스킵(`EMPTY`),
   법정동을 못 찾거나 뒤에 숫자(지번 본번/부번)가 안 붙어있으면 스킵
   (`UNPARSEABLE_OR_DONG_NOT_FOUND`). "번지"와 "호" 둘 다 지번 종결어로 인정(옛날 데이터는
   "호"로 끝나는 경우가 많음 — 마스킹이 아니라 구식 표기).
4. **인허가일자 없는 행 스킵**(`NO_LICENSED_AT`) — 스키마상 `licensed_at NOT NULL`이라 필수.
5. 통과한 행만 `licensed-business-records-NNN.sql`로 1000행 단위 청크 저장(기존 청크 개수 다음
   번호부터 이어붙임).

## PNU 계산 원리 (`jibun_pnu.py`)

Korean PNU = **법정동코드(10자리) + 산여부(1자리) + 본번(4자리) + 부번(4자리) = 19자리**.

1. 지번주소 문자열에서 `legaldong_codes.csv`에 있는 법정동명(동 이름)을 찾는다 — 가장 긴
   전체경로 이름부터 검사(부분 문자열 오매칭 방지).
2. 동 이름 뒤에 오는 텍스트에서 정규식으로 `(산)?(본번)(-(부번))?`을 추출. 뒤에 건물명 같은
   부가 텍스트가 붙어 있어도 무시하고 앞부분만 씀.
3. 매칭 실패(동 이름 자체가 없거나, 동 이름 뒤에 숫자가 안 나오는 잡텍스트)면 `None` 반환.

**중요 — REGION_FILTER**: `parse_licensed_records.py`가 `load_legaldong_codes()`로 전국
법정동코드를 다 불러온 뒤, 실제 `parse_pnu()`에 넘기기 전에 `"성남시"`가 이름에 포함된 것만
걸러서 넘긴다. 동 이름은 전국적으로 흔하게 겹친다(예: "태평동"이 성남시 외에도 대전·전주·창원·
통영에 각각 존재) — 지역으로 후보를 안 좁히면 엉뚱한 도시의 PNU가 나온다(2026-07-17 세션에서
실제로 겪은 버그, `docs/superpowers/plans/2026-07-17-licensed-record-csv-ingestion.md`의
"실행 중 발견된 버그와 수정" 참고). 성남시 내부에서는 동 이름 충돌이 0건임을 확인했으므로 이
필터만으로 완전히 안전함.

## 알려진 한계

- `parsed_floor`/`parsed_unit_no`/`parsed_building_name` 등 상세주소 파싱 필드는 전부 `NULL` —
  이 원본들엔 상세주소 파싱 근거가 없음. 기존 `AddressDetailParser`(Java, 도로명주소 기반)가
  조회 시점 전에 별도로 채워야 함(이 스크립트 스코프 아님).
- 좌표(X/Y)는 원본값을 그대로 저장 — 좌표계 변환(EPSG:5174→WGS84)은 Java
  `KoreanTmCoordinateConverter`가 조회 시점에 처리. 이 스크립트에서 재구현하지 않음.
- `_LICENSE_NO_PATTERN`(관리번호 중복 제거용 정규식)이 `to_insert_row()`가 만드는 컬럼 순서
  (`id, pnu, category, sub_category, license_no, ...`)에 암묵적으로 결합돼 있음 — 컬럼 순서를
  바꾸면 이 정규식도 같이 고쳐야 함.
- 성남시 외 지역 지원, 인코딩 자동감지, 컬럼명 자동매핑은 전부 의도적으로 안 만듦(YAGNI) — 필요할
  때 그만큼만 확장할 것.

## 관련 문서

- `docs/superpowers/specs/2026-07-17-licensed-record-csv-ingestion-design.md` — 최초 설계(왜
  이런 구조인지, PNU 유도가 왜 이 프로젝트에 없었던 신규 기능인지)
- `docs/superpowers/specs/2026-07-17-large-csv-ingestion-utility-design.md` — 전국판 대용량
  원본 대응 확장 설계(성남시 사전 필터, 관리번호 중복 제거)
- `docs/superpowers/plans/2026-07-17-licensed-record-csv-ingestion.md`,
  `docs/superpowers/plans/2026-07-17-large-csv-ingestion-utility.md` — 구현 계획 + 실행 중
  발견된 버그(동명 충돌) 기록
