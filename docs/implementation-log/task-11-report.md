# Task 11 실행 보고서 — 로컬 실행 스모크 테스트

**결과: DONE**

## 실행 환경
- 작업 디렉토리: `D:\Dev\_Woowahan-Techcourse\woowaTon\server`
- `SANGGA_SERVICE_KEY` 환경변수에 실제 서비스키(디코딩 형태) 주입 후 `mvn spring-boot:run` 실행. 값 자체는 본 보고서에 기록하지 않음.
- 참고: 첫 번째 기동 시도(PowerShell 툴 호출을 통한 백그라운드 실행)는 로그상 `Started NextstepApplication`까지 찍혔으나 이후 프로세스가 사라지고 포트 8080 리스너도 없어짐(툴 프로세스 라이프사이클 이슈로 추정, 코드 문제 아님). `Start-Process`로 완전히 분리된 프로세스로 재기동하여 정상 확인함. 이 과정에서 이 세션과 무관한 이전 orphan 인스턴스 2개(포트 8081, 8090)를 발견했으나 손대지 않음(8080과 충돌 없음).

## Step 1: 서버 기동
- 재기동 PID 31020(Start-Process로 분리 실행).
- 로그: `Tomcat started on port 8080 (http) with context path '/'` → `Started NextstepApplication in 3.055 seconds`.
- 예외/ERROR 로그 없음.

## Step 2: `GET /api/sites/search?query=신흥동`
```json
{"candidates":[
  {"pnu":"4113110100100300002","jibunAddress":"경기도 성남시 수정구 신흥동 30-2 2층","roadAddress":"경기도 성남시 수정구 수정로 287, 2층 (신흥동)","latitude":37.4502874,"longitude":127.1464099,"unitCount":1,"closedCount":0},
  {"pnu":"4113110100100340000","jibunAddress":"경기도 성남시 수정구 신흥동 34 수정빌딩","roadAddress":"경기도 성남시 수정구 수정로 273, 수정빌딩 1층 (신흥동)","latitude":37.4492216,"longitude":127.1456208,"unitCount":1,"closedCount":1}
]}
```
- `candidates` 2건, `spec/api-spec.md` ① 스키마(pnu/jibunAddress/roadAddress/latitude/longitude/unitCount/closedCount) 필드 전부 일치. 기대대로.

## Step 3: `GET /api/sites/4113110300100280001`
```json
{"site":{...},"units":[{"unitId":"4113110300100280001-U1","label":"단일 점포","currentBusinessName":null,"currentStatus":"공실","totalTenancyCount":2,"closedCount":2,"averageSurvivalMonths":45,"industryDetail":null,"locationSource":"license"}],"disclaimer":{"dataAsOf":"2026-07-10","note":"인허가 신고 기준 데이터로 실제 영업 현황과 차이가 있을 수 있습니다."}}
```
- `units[0].closedCount` = 2 (기대값과 일치), `disclaimer` 필드 포함. 기대대로.

## Step 4: `GET /api/units/4113110100100340000-U1`
```json
{"unit":{...},
 "statistics":{"totalTenancyCount":2,"closedCount":1,"averageSurvivalMonths":87,"longestSurvivalMonths":87,"shortestSurvivalMonths":87},
 "timeline":[
   {"tenancyId":"t-90001","businessName":"구정 동물미용실",...,"status":"폐업","marketInfo":{"isPlaceholder":true,"leaseAreaSqm":42.6,"depositKrw":50000000,"monthlyRentKrw":2800000,"keyMoneyKrw":0,"dailyFloatingPopulation":21400,"sameCategoryNearbyCount":null,"vacancyRatePercent":6.2,"asOf":"2026-07-10"}},
   {"tenancyId":"t-9799","businessName":"동물병원 더 하임",...,"status":"영업","marketInfo":{"isPlaceholder":true,"leaseAreaSqm":42.6,"depositKrw":50000000,"monthlyRentKrw":2800000,"keyMoneyKrw":0,"dailyFloatingPopulation":21400,"sameCategoryNearbyCount":null,"vacancyRatePercent":6.2,"asOf":"2026-07-10"}}
 ],
 "disclaimer":{...}}
```
- `timeline` 2건, 각 `marketInfo.isPlaceholder` = true.
- `sameCategoryNearbyCount` = **null** (두 이력 모두). 이 샌드박스에서 apis.data.go.kr 아웃바운드가 막혀 있어 예상된 결과 — HTTP 200으로 정상 응답했고 500 에러 없음. 경계 B try-catch(상가API 반경조회 실패 격리)가 의도대로 동작함이 확인됨.
- 나머지 목업 5필드(leaseAreaSqm/depositKrw/monthlyRentKrw/keyMoneyKrw/dailyFloatingPopulation/vacancyRatePercent)는 상수값으로 정상 채워짐.

## Step 5: 에러 케이스
| 케이스 | 기대 | 실제 |
|---|---|---|
| `GET /api/sites/search` (query 없음) | 400 | 400 |
| `GET /api/sites/0000000000000000000` (없는 pnu) | 404 | 404 |
| `GET /api/units/no-such-unit` (없는 unitId) | 404 | 404 |

전부 기대대로.

## Step 6: 서버 종료
- `Stop-Process -Id 31020 -Force` 로 종료, 이후 포트 8080 리스너 없음 확인. 코드 변경 없었으므로 커밋 스킵.

## 결론
5단계 검증 전부 기대대로 동작. `sameCategoryNearbyCount`는 null(네트워크 차단 환경 기대값)이며, 경계 B의 실패 격리 경로가 크래시 없이 정상 동작함을 확인. 실제 상가API 아웃바운드가 뚫리는 배포 환경(Railway)에서는 이 값이 정수로 채워질 것으로 예상되며 이는 별도 검증 필요(이 세션 범위 밖).
