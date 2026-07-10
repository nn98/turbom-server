# Task 8 리포트: web DTO + SiteController + GlobalExceptionHandler

## 상태: DONE_WITH_CONCERNS

## 요약

브리프(`task-8-brief.md`)의 Step 1~8을 순서대로 그대로 실행했다. 코드는 브리프에 주어진 그대로 옮겨 적었으며(설계 변경 없음), `SiteQueryService`가 `web.dto.ApiDtos`를 직접 import하는 구조도 브리프 의도대로 유지했다.

## 실행한 TDD 절차

1. **Step 1**: `server/src/test/java/com/nextstep/web/SiteControllerTest.java`를 브리프 원문 그대로 작성.
2. **Step 2**: `mvn -q test -Dtest=SiteControllerTest` 실행 → 6개 테스트 전부 실패 확인 (컨트롤러 부재로 전부 404, 라우트 없음). 실패 로그 확인 완료.
3. **Step 3**: `server/src/main/java/com/nextstep/web/dto/ApiDtos.java` 작성 (record 9종 + ErrorResponse).
4. **Step 4**: `server/src/main/java/com/nextstep/application/SiteQueryService.java` 작성. `MarketInfo.unavailable()`을 고정값으로 사용(Task 9에서 실제 상가API 연동 예정).
5. **Step 5**: `server/src/main/java/com/nextstep/web/SiteController.java` 작성 (3 엔드포인트: `GET /api/sites/search`, `GET /api/sites/{pnu}`, `GET /api/units/{unitId}`).
6. **Step 6**: `server/src/main/java/com/nextstep/web/GlobalExceptionHandler.java` 작성 (`InvalidQueryException`→400 `INVALID_QUERY`, `SiteNotFoundException`/`UnitNotFoundException`→404, 그 외→500 `INTERNAL_ERROR`).
7. **Step 7**: `mvn -q test -Dtest=SiteControllerTest` 재실행 → **6/6 통과** (`Tests run: 6, Failures: 0, Errors: 0`).
8. **Step 8**: 커밋 완료 (`git add` 대상은 브리프 지정 경로 그대로: `web`, `application/SiteQueryService.java`, `test/.../web`).

기존 도메인 클래스(`Site`, `Unit`, `Tenancy`, `UnitStatistics`, `MarketInfo`, `LocationSource`, `BusinessStatus`, 예외 3종, `TenancyQueryService`)를 사전에 전부 읽고 브리프 코드의 시그니처·필드명이 실제 구현과 정확히 일치함을 확인한 뒤 작성했다. 필드명 불일치나 시그니처 문제는 없었다.

## 브리프와 다르게 처리한 부분

없음. 브리프의 코드를 파일별로 그대로 옮겨 적었다.

## 우려사항 (DONE_WITH_CONCERNS 사유)

**태스크가 명시한 검증 커맨드(`mvn -q test -Dtest=SiteControllerTest`, 단독 실행)는 6/6 통과했다.** 다만 `mvn test`로 전체 스위트를 함께 돌리면 `SiteControllerTest`의 6개 테스트가 모두 `ApplicationContext` 로딩 실패로 에러가 난다.

**원인**: 이건 이번에 작성한 web 계층 코드의 버그가 아니라, 기존 `application.yml`의 테스트 DB 설정과 Spring 테스트 컨텍스트 캐싱 방식이 충돌하는 선재(pre-existing) 인프라 문제다.

- `application.yml`이 이름 있는 인메모리 H2(`jdbc:h2:mem:nextstep;DB_CLOSE_DELAY=-1`)를 `sql.init.mode=always`와 함께 씀 → 컨텍스트가 새로 뜰 때마다 `schema.sql`을 재실행.
- 기존 `TenancyQueryServiceTest`(`@SpringBootTest` 단독)와 이번에 추가한 `SiteControllerTest`(`@SpringBootTest` + `@AutoConfigureMockMvc`)는 설정 시그니처가 달라 Spring이 **서로 다른 캐시 키**로 별도 컨텍스트를 띄운다.
- 첫 컨텍스트가 스키마를 만든 뒤에도 `DB_CLOSE_DELAY=-1`이라 DB가 살아있는 상태에서, 두 번째 컨텍스트가 다시 `CREATE TABLE`을 시도 → H2가 "Table already exists"로 실패.
- 재현 확인: `mvn test -Dtest=TenancyQueryServiceTest,SiteControllerTest`만 같이 돌려도 동일하게 재현됨(`PersistenceSmokeTest`는 `@DataJpaTest`라 별도 임베디드 DB를 써서 무관함을 확인).

이 문제는 `@SpringBootTest` 계열 설정이 서로 다른 두 번째 테스트 클래스가 생기는 순간부터 잠재해 있던 것으로, 이번 태스크 범위(web 계층 구현) 밖이다. 브리프가 "코드는 그대로 옮겨 적으라"고 명시했으므로 `application.yml`이나 테스트 DB 전략은 건드리지 않았다. 다음 태스크(혹은 별도 인프라 태스크)에서 `@DirtiesContext`, 랜덤 포트/DB명, 또는 `sql.init.mode` 조정 중 하나로 해소가 필요하다.

## 최종 검증

- `mvn -q test -Dtest=SiteControllerTest` → **Tests run: 6, Failures: 0, Errors: 0** (단독 실행, 브리프 지정 커맨드)
- `mvn test`(전체 스위트) → `SiteControllerTest` 6개 에러(위 원인), 나머지 12개(`TenancyPeriodTest` 3, `UnitStatisticsTest` 2, `PersistenceSmokeTest` 3, `TenancyQueryServiceTest` 4)는 정상 통과
- git 커밋: `df0064f` — "feat: add 3 API endpoints with DB-only pipeline (marketInfo stubbed unavailable)"
