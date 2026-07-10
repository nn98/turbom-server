### Task 11: 로컬 실행 스모크 테스트 (실제 서비스키로 실패 격리 경로 확인)

**Files:** 없음 (실행/검증만).

- [ ] **Step 1: 서버 기동**

```bash
cd server
SANGGA_SERVICE_KEY='<발급받은 디코딩 키>' mvn spring-boot:run
```

Expected: `Started NextstepApplication` 로그, 예외 없음.

- [ ] **Step 2: 검색 엔드포인트 확인**

```bash
curl -s "http://localhost:8080/api/sites/search?query=신흥동" | head -c 500
```

Expected: `spec/api-spec.md` ① 스키마와 일치하는 JSON, `candidates` 2건.

- [ ] **Step 3: 자리 상세 엔드포인트 확인**

```bash
curl -s "http://localhost:8080/api/sites/4113110300100280001" | head -c 800
```

Expected: `units[0].closedCount` 2, `disclaimer` 포함.

- [ ] **Step 4: 물건 상세 엔드포인트 확인 (marketInfo 실패 격리 경로)**

```bash
curl -s "http://localhost:8080/api/units/4113110100100340000-U1" | head -c 1500
```

Expected: `timeline` 2건, 각 `marketInfo.isPlaceholder=true`. `sameCategoryNearbyCount`는 이 샌드박스에서 apis.data.go.kr 아웃바운드가 막혀 있으므로 `null`이 기대값 — 500 에러 없이 200이 내려오면 실패 격리(경계 B try-catch)가 의도대로 동작한 것.

- [ ] **Step 5: 에러 케이스 확인**

```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/sites/search"
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/sites/0000000000000000000"
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/units/no-such-unit"
```

Expected: `400`, `404`, `404` 순.

- [ ] **Step 6: 서버 중단, 최종 커밋 없음(코드 변경 없었으므로 스킵)**

---

## Self-Review 메모 (계획 작성자 기록)

- **스펙 커버리지**: `api-spec.md` 3엔드포인트 전부 Task 8·10에서 구현. `backend-spec.md` §10 1~6단계는 Task 1(1)→Task 3-6(2~4)→Task 9(5)→Task 10(6)에 대응. `schema.sql`은 Task 2에서 그대로 복사. marketInfo 6 목업 필드는 Task 7 상수 + Task 8 DTO 매핑에서 전부 채워짐. 상가API BASE_URL·오퍼레이션 함정(`storeListInRadius` vs `storeZoneInRadius`)은 Task 9에서 반영.
- **범위 밖으로 명시한 것**: 대용량 실데이터 적재(`ingestion/` 포팅), VWorld 지오코딩, Railway 배포 — 전부 Global Constraints에 스코프 아웃 명시.
- **타입 일관성**: `Tenancy.subCategory()`(Task 4)가 Task 10의 `representativeSubCategory` 계산에 그대로 쓰였고, `MarketInfo.sameCategoryNearbyCount()`(Task 7)가 Task 8·10에서 동일 이름으로 쓰임. `TenancyQueryService.UnitWithSite`(Task 6)가 Task 8·10의 `getUnitDetail`에서 그대로 소비됨 — 이름 불일치 없음.
