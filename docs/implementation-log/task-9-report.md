# Task 9 Report: SanggaApiClient

## 상태: DONE

## 진행 방식

브리프(`task-9-brief.md`)에 완성된 코드가 그대로 있어 Step 1~7을 순서대로 수행했다. TDD 절차 그대로 따름:

1. **Step 1**: `SanggaApiClientTest.java`를 브리프 원문 그대로 생성 (`server/src/test/java/com/nextstep/infra/sangga/`).
   - 브리프 주의사항대로, `setUp()`에서 `MockRestServiceServer.bindTo(builder)`로 바인딩한 뒤 `client = new SanggaApiClient(builder, ...)` — `builder`(바인딩된 `RestClient.Builder`) 그대로 넘김. `builder.build()`를 넘기지 않았음(타입 불일치로 컴파일 안 됨을 사전에 확인한 지시사항).
2. **Step 2**: `mvn -q test -Dtest=SanggaApiClientTest` 실행 → 예상대로 컴파일 에러 3건 확인(`SanggaApiClient`, `SanggaProperties` 클래스 없음). 실패하는 테스트임을 확인.
3. **Step 3**: `SanggaProperties.java`(`@ConfigurationProperties(prefix = "sangga.api")` record) 브리프 그대로 생성.
4. **Step 4**: `SanggaStoreListResponse.java`(header/body/items 중첩 record) 브리프 그대로 생성.
5. **Step 5**: `SanggaApiClient.java`(`@Component`, 생성자 `(RestClient.Builder, SanggaProperties)`, `countSameCategoryInRadius`) 브리프 그대로 생성.
6. **Step 6**: `mvn -q test -Dtest=SanggaApiClientTest` 재실행 → `Tests run: 2, Failures: 0, Errors: 0` (surefire report로 확인, `-q` 플래그라 콘솔엔 출력 없음).
7. **Step 7**: `git add src/main/java/com/nextstep/infra/sangga src/test/java/com/nextstep/infra/sangga` + 커밋.

## 브리프와 다르게 처리한 부분

없음. 브리프의 코드를 문자 그대로 옮겨 적었고, 별도 설계 판단이나 수정을 가하지 않았다.

## 추가 확인 사항 (변경 아님, 검증만)

- `SanggaApiClient`는 `@Component`이고 생성자가 `SanggaProperties` 빈을 요구하는데, `SanggaProperties`는 `@ConfigurationProperties`만 붙어 있어 별도 등록이 필요한지 확인함. `NextstepApplication.java`에 이미 `@ConfigurationPropertiesScan`이 걸려 있어(기존 코드, 이번 작업으로 추가한 것 아님) 자동 등록됨 — `application.yml`에 `sangga.api.*` 프로퍼티가 없어도 컨텍스트 로딩은 실패하지 않음(값은 null로 바인딩, 이 태스크 범위 밖).
- 이 변경이 기존 `@SpringBootTest` 전체 컨텍스트 로딩 테스트(`TenancyQueryServiceTest`, `SiteControllerTest`)를 깨지 않는지 확인하려고 전체 테스트 스위트(`mvn -q test`, 옵션 없이)를 돌림 — 6개 테스트 클래스, 총 20개 테스트 전부 통과(`Failures: 0, Errors: 0`).

## 테스트 결과

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.nextstep.infra.sangga.SanggaApiClientTest
```

전체 스위트: 6 test classes, 20 tests, 0 failures, 0 errors.

## 커밋

`dccef87` — `feat: add SanggaApiClient for storeListInRadius`

```
4 files changed, 118 insertions(+)
create mode 100644 src/main/java/com/nextstep/infra/sangga/SanggaApiClient.java
create mode 100644 src/main/java/com/nextstep/infra/sangga/SanggaProperties.java
create mode 100644 src/main/java/com/nextstep/infra/sangga/SanggaStoreListResponse.java
create mode 100644 src/test/java/com/nextstep/infra/sangga/SanggaApiClientTest.java
```
