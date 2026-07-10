# Post-Task-11 Session Log

`task-1..11-{brief,report}.md`와 `progress.md`는 [subagent-driven-development](https://github.com/obra/superpowers) 워크플로로 진행된 최초 11개 태스크(`docs/superpowers/plans/2026-07-09-backend-server.md`)의 기록이다 — 각 태스크를 브리프로 만들어 구현자 서브에이전트에 디스패치하고, 리뷰어 서브에이전트가 검증한 기록.

Task 11(로컬 실행 스모크 테스트) 완료 이후는 이 형식적인 브리프/리뷰 사이클 없이 컨트롤러(메인 세션)가 직접 진행했다 — 서브에이전트 로그가 없으므로 이 문서에 요약을 남긴다. 커밋 해시는 `git log`로 확인 가능(전부 `main` 브랜치).

## 1. Sangga 서비스키 URL 인코딩 버그 (401 Unauthorized)

`sameCategoryNearbyCount`가 항상 `null`이었던 원인 진단. `SanggaApiClient`가 Spring `RestClient`의 기본 URI 빌더로 서비스키를 넘기고 있었는데, 이 빌더는 RFC 3986 기준 `+`를 안전한 문자로 보고 인코딩하지 않는다. data.go.kr은 `application/x-www-form-urlencoded` 관례대로 `+`를 공백으로 해석해 base64 계열 서비스키가 손상됐다(401). `URLEncoder`로 직접 인코딩한 뒤 이미 인코딩된 `URI`로 요청하도록 수정. 실API로 검증(정상 응답 확인).

## 2. 소분류 → 상가 대분류 코드 매핑 (`IndustryCategoryMapper`)

애초 설계는 우리 소분류를 상가API 소분류코드(`indsSclsCd`)에 매핑하는 것이었으나 공식 매핑표가 없어 브레인스토밍 단계에서 보류됐었다(`sameCategoryNearbyCount`가 늘 0에 가깝게 나오는 원인이기도 했음). 상가API를 직접 샘플링해 대분류코드 10종(G2 소매·I1 숙박·I2 음식·L1 부동산·M1 과학·기술·N1 시설관리·임대·P1 교육·Q1 보건의료·R1 예술·스포츠·S2 수리·개인)을 확보하고, 실 데이터에 존재하는 소분류 136종을 이 10종에 손수 매핑(`IndustryCategoryMapper`). 매핑 없는 소분류(도축업·제조업·도매업 등, "상가 상권" 개념 자체가 없는 인허가 업종)는 API 호출 자체를 스킵.

## 3. marketInfo 확장 — `totalStoreCount`/`categoryBreakdown`

"동일업종 개수 하나뿐이라 상권 정보가 빈약하다"는 지적에 따라 반경 내 전체 점포수와 업종(대분류)별 분포·비중을 추가. 별도 API 호출 1건(업종 필터 없음, `numOfRows=500`)으로 `totalCount`+`items[].indsLclsCd` 집계. `sameCategoryNearbyCount`와 독립적으로 실패 격리(하나만 null이어도 나머지는 정상). "최근개업수"(`chgGb`/`chgDt` 필드)도 추가 가능한지 조사했으나 실API 응답 39개 필드 전수 확인 결과 해당 필드가 존재하지 않아 포기 — `상권조회-API-명세.md`의 관련 서술이 부정확한 것으로 결론.

`spec/api-spec.md`·`spec/backend-spec.md`·`spec/frontend-spec.md`에 반영(스냅샷+CHANGELOG 포함).

## 4. HTTP 타임아웃 (`SanggaRestClientConfig`)

최종 전체 리뷰에서 발견: `SanggaApiClient`에 타임아웃이 없어 상가API가 느리거나(네트워크가 패킷을 그냥 드롭하는 경우) 요청 스레드가 오래 멈출 수 있었음. `RestClientCustomizer` 빈으로 커넥트 2초/리드 3초를 자동구성 빌더에 적용(테스트에서 쓰는 수동 `RestClient.builder()`엔 영향 없음 — Spring 컨텍스트를 안 타는 순수 단위테스트라 커스터마이저 자체가 적용 안 됨, 바이트코드 분석으로 검증).

## 5. 백엔드-스펙 동기화 (구현 변경 없음)

11차까지 스펙 문서가 코드를 못 따라간 부분(클래스명 오타 `SangaApiClient`→`SanggaApiClient`, 상권 조회 방식이 실제로는 소분류코드가 아니라 대분류코드 기준이라는 사실, Java 17→21, H2 파일모드→인메모리 등)을 전수 점검해 정정. `spec/schema.sql`도 캐노니컬 사본이 실제보다 5개 컬럼(주소 파싱 결과) 뒤처져 있던 걸 동기화.

## 6. 실데이터 규모 확장 + OOM 대응 (다른 컨트리뷰터와 동시 진행, 병합됨)

인허가 실데이터가 47,532건 → 84,254건으로 늘어나면서, 22MB짜리 단일 `data.sql`(거대 INSERT 1건)이 로컬 로딩·테스트 중 OOM을 유발. 정확히 같은 문제를 로컬(이 세션)과 원격(다른 컨트리뷰터, 커밋 `3a0f610 fix: dataset oom 오류해결`)이 동시에 각자 고쳤다 — 둘 다 `data.sql`을 `data/licensed-business-records-{001..085}.sql`로 분할하는 동일한 해법에 도달. `git pull`이 두 로컬 변경이 충돌해 abort되면서 발견됨, 수동 병합 후 push(`0e2251d`). 테스트용 힙도 `-Xmx768m`으로 증량.

**DB 전환 관련 결정(사용자 확정)**: 이미 AWS에 배포돼 있으나 외부 접근이 불가능한 서버라 Postgres 같은 외부 DB로 전환할 수 없음 — H2 인메모리 + 분할 파일 로드를 유지하는 게 현재로선 유일한 방법. Firebase 분리는 검토 후 기각(NoSQL로는 지금의 관계형 쿼리 패턴(LIKE 주소검색, PNU 조인)을 못 살림, 조회 계층 전체 재작성 필요). 네트워크 접근이 열리면 Postgres 전환이 정석(`backend-spec.md` §7에 이미 기록).

## 7. 로컬 워킹 디렉토리 파일 잠금 이슈 (미해결, 팀원 조치 필요)

`D:\Dev\_Woowahan-Techcourse\woowaTon\server` 로컬 체크아웃에서 `git pull`이 `src/main/resources/data.sql` 경로를 계속 "unlink 불가(Invalid argument)"로 실패시키는 문제 발견. IntelliJ 재시작으로도 해결 안 됨 — 삭제한 파일(`spec/FRONTEND-INTEGRATION-CONTRACT.md`)이 반복적으로 되살아나는 것도 함께 관찰돼, 클라우드 동기화(OneDrive 등) 또는 실시간 백신 검사가 원인일 가능성이 높다고 추정. 원격 저장소(GitHub)는 항상 정상 상태로 유지했고(임시 클론에서 병합·검증·push), 로컬 동기화만 막힌 상태로 남아있다. 다음 작업자는 이 폴더를 클라우드 동기화/백신 예외 목록에 추가하거나, 필요하면 로컬 폴더를 재클론해서 교체할 것.

## 재사용 가능한 서브에이전트

이 로그와 별도로, subagent-driven-development에서 쓴 구현자/리뷰어 프롬프트 패턴을 프로젝트 레벨 서브에이전트로 저장해뒀다: `.claude/agents/tdd-implementer.md`, `.claude/agents/task-reviewer.md`. 이 저장소를 clone한 팀원은 Claude Code에서 `subagent_type: tdd-implementer` / `task-reviewer`로 바로 재사용할 수 있다.
