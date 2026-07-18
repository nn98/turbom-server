# 경매정보 수집 스파이크 (개발용) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** courtauction.go.kr에서 경매 사건 정보를 수집·파싱·저장하는 **개발/내부 검증용** 파이프라인을 만든다. 공개 API나 프론트에는 절대 노출하지 않는다.

**Architecture:** Playwright-Java로 courtauction.go.kr의 실제 화면(WebSquare)을 그대로 구동해 렌더링된 텍스트를 얻는다. 그 텍스트를 순수 함수(파서)로 도메인 레코드(`AuctionCase`, `AuctionScheduleEntry`)로 변환한다. 파서는 이번 세션에 브라우저로 직접 확인한 실제 캡처 텍스트를 고정 픽스처로 TDD한다. 수집기(`CourtAuctionCollector`)는 Playwright 오케스트레이션만 담당하고 파싱 로직은 갖지 않는다. 저장은 JPA 엔티티 2개(`AuctionCaseEntity`, `AuctionScheduleEntryEntity`)로 하되, `@Scheduled`도 없고 REST 컨트롤러 연결도 없다 — 개발자가 수동으로 트리거하는 서비스 메서드 하나가 전부다.

**Tech Stack:** Spring Boot 3.3.4 / Java 21 / JPA(H2) / Playwright-Java 1.52.0(Maven Central `search.maven.org`로 2026-07-19 확인, 실행 전 재확인 권장) / JUnit 5 + AssertJ(기존 컨벤션)

## Global Constraints

- **공개 API 연결 금지**: `spec/api-spec.md`에 어떤 필드도 추가하지 않는다. 이 스파이크의 산출물은 어떤 `@RestController`에도 연결되지 않는다.
- **자동 실행 금지**: `@Scheduled`, `CommandLineRunner`, `ApplicationRunner` 어디에도 수집 로직을 등록하지 않는다. 개발자가 테스트나 IDE에서 메서드를 직접 호출해야만 실행된다.
- **실사이트를 치는 테스트는 기본 `mvn test`에서 절대 실행되지 않아야 한다**: 클래스명에 `Test`/`Tests`/`TestCase` 접미사를 쓰지 않는다(Surefire 기본 include 패턴 `**/*Test.java`, `**/Test*.java`, `**/*Tests.java`, `**/*TestCase.java`를 피하기 위함). 대신 `*ManualCheck.java`, `*SmokeCheck.java`처럼 짓고, `mvn test -Dtest=클래스명`으로만 수동 실행한다. CI(`ci-cd.yml`)는 `mvn test`만 돌리므로 이 네이밍만 지키면 별도 pom 설정 없이 자동 제외된다.
- **패키지 컨벤션 준수**: 도메인 레코드는 `com.nextstep.domain.auction`, 인프라(Playwright/파서)는 `com.nextstep.infra.auction`, 영속성은 `com.nextstep.infra.persistence`, 수동 트리거 서비스는 `com.nextstep.application.auction` — 기존 `domain`/`infra`/`application` 3분류를 따른다.
- **이 문서의 근거 문서**: `docs/superpowers/specs/2026-07-18-auction-data-integration-design.md` (status: 부분 승인). 이용약관 제15조 이슈로 실서비스 노출은 CODEF/법원행정처 동의 확인 전까지 보류 — 이 플랜은 그 승인 범위(스키마·수집코드 개발) 안에서만 작업한다.

---

### Task 1: Playwright-Java 의존성 추가 + 헤드리스 브라우저 스모크 테스트

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/com/nextstep/infra/auction/PlaywrightSmokeCheck.java`

**Interfaces:**
- Produces: `com.microsoft.playwright.Playwright`, `Browser`, `Page` 클래스가 클래스패스에서 사용 가능해짐. 이후 태스크는 `Playwright.create()` → `playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))` → `browser.newPage()` 순서로 사용한다.

- [ ] **Step 1: Maven Central에서 최신 버전 재확인**

  다음 명령으로 현재 최신 릴리즈를 확인한다 (2026-07-19 확인 시점 기준 `1.52.0` — 실행 시점에 더 최신이 있으면 그 버전을 쓴다):

  ```bash
  curl -s "https://search.maven.org/solrsearch/select?q=g:%22com.microsoft.playwright%22+AND+a:%22playwright%22&core=gav&rows=1&wt=json"
  ```

  응답의 `response.docs[0].v` 필드가 버전이다.

- [ ] **Step 2: pom.xml에 의존성 추가**

  `<dependencies>` 블록의 `h2` 의존성 다음에 추가:

  ```xml
    <dependency>
      <groupId>com.microsoft.playwright</groupId>
      <artifactId>playwright</artifactId>
      <version>1.52.0</version>
      <scope>test</scope>
    </dependency>
  ```

  (스파이크가 프로덕션 코드 경로에 섞이지 않도록 `test` 스코프로 시작한다. Task 5에서 실제 수집기를 `src/main`에 두려면 스코프를 `compile`로 바꿔야 하는데, 그건 Task 5에서 판단한다 — Task 1은 스모크 테스트만 통과시키면 된다.)

- [ ] **Step 3: 브라우저 바이너리 설치**

  ```bash
  mvn dependency:resolve -Dtest=none
  mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" -Dexec.classpathScope=test
  ```

  `exec-maven-plugin`이 pom에 없으면 대신 다음으로 설치한다(테스트 스코프 클래스패스에서 직접 실행):

  ```bash
  mvn test-compile
  java -cp "target/test-classes;target/classes;$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dscope=test)" com.microsoft.playwright.CLI install chromium
  ```

  (Windows PowerShell에서는 classpath 구분자가 `;`이므로 위 그대로 사용, bash에서 실행 중이면 동일하게 `;` 유지 — Playwright CLI는 OS와 무관하게 Java classpath 구분자를 그대로 받는다.)

- [ ] **Step 4: 스모크 테스트 작성**

  ```java
  package com.nextstep.infra.auction;

  import com.microsoft.playwright.Browser;
  import com.microsoft.playwright.BrowserType;
  import com.microsoft.playwright.Page;
  import com.microsoft.playwright.Playwright;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;

  class PlaywrightSmokeCheck {

      @Test
      void 헤드리스_브라우저가_실행되고_페이지_타이틀을_읽는다() {
          try (Playwright playwright = Playwright.create()) {
              Browser browser = playwright.chromium()
                  .launch(new BrowserType.LaunchOptions().setHeadless(true));
              Page page = browser.newPage();
              page.setContent("<html><head><title>스모크 테스트</title></head><body>ok</body></html>");
              assertThat(page.title()).isEqualTo("스모크 테스트");
              browser.close();
          }
      }
  }
  ```

- [ ] **Step 5: 수동 실행으로 확인**

  ```bash
  mvn test -Dtest=PlaywrightSmokeCheck
  ```

  Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 6: 기본 `mvn test`에서 제외되는지 확인**

  ```bash
  mvn test 2>&1 | grep -i PlaywrightSmokeCheck
  ```

  Expected: 아무 출력도 없음 (Surefire가 이 클래스를 아예 스캔하지 않았다는 뜻)

- [ ] **Step 7: 커밋**

  ```bash
  git add pom.xml src/test/java/com/nextstep/infra/auction/PlaywrightSmokeCheck.java
  git commit -m "chore: add Playwright-Java for court-auction collection spike"
  ```

---

### Task 2: 도메인 레코드 — AuctionCaseRef, AuctionScheduleEntry, AuctionCase

**Files:**
- Create: `src/main/java/com/nextstep/domain/auction/AuctionCaseRef.java`
- Create: `src/main/java/com/nextstep/domain/auction/AuctionScheduleEntry.java`
- Create: `src/main/java/com/nextstep/domain/auction/AuctionCase.java`

**Interfaces:**
- Produces (Task 3이 소비): `AuctionCaseRef(String caseNumber, int itemNumber)`
- Produces (Task 4가 소비): `AuctionScheduleEntry(String scheduleDate, String scheduleTime, String scheduleType, String location, BigDecimal minimumPriceKrw, String result)`, `AuctionCase` 전체 필드(아래)

이 태스크는 순수 데이터 구조라 실패하는 테스트 없이 바로 작성한다(레코드 자체는 동작이 없음, `Tenancy.java` 스타일 참고 — 단 이번 레코드들엔 파생 메서드가 없어 record 컴팩트 생성자만 있으면 됨).

- [ ] **Step 1: AuctionCaseRef 작성**

  ```java
  package com.nextstep.domain.auction;

  public record AuctionCaseRef(String caseNumber, int itemNumber) {
  }
  ```

- [ ] **Step 2: AuctionScheduleEntry 작성**

  ```java
  package com.nextstep.domain.auction;

  import java.math.BigDecimal;

  public record AuctionScheduleEntry(
      String scheduleDate,
      String scheduleTime,
      String scheduleType,
      String location,
      BigDecimal minimumPriceKrw,
      String result
  ) {
  }
  ```

- [ ] **Step 3: AuctionCase 작성**

  ```java
  package com.nextstep.domain.auction;

  import java.math.BigDecimal;
  import java.util.List;

  public record AuctionCase(
      String caseNumber,
      int itemNumber,
      String court,
      String divisionName,
      String propertyType,
      String jibunAddress,
      BigDecimal appraisalValueKrw,
      BigDecimal minimumSalePriceKrw,
      BigDecimal bidDepositKrw,
      String biddingMethod,
      String saleDate,
      String filedDate,
      String auctionStartDate,
      String claimDeadline,
      BigDecimal claimAmountKrw,
      String appraisalSummary,
      List<AuctionScheduleEntry> scheduleHistory
  ) {
  }
  ```

- [ ] **Step 4: 컴파일 확인**

  ```bash
  mvn compile
  ```

  Expected: `BUILD SUCCESS`

- [ ] **Step 5: 커밋**

  ```bash
  git add src/main/java/com/nextstep/domain/auction/
  git commit -m "feat: add auction domain records"
  ```

---

### Task 3: AuctionListParser — 목록 화면에서 사건 참조 추출

**Files:**
- Create: `src/main/java/com/nextstep/infra/auction/AuctionListParser.java`
- Test: `src/test/java/com/nextstep/infra/auction/AuctionListParserTest.java`

**Interfaces:**
- Consumes: `com.nextstep.domain.auction.AuctionCaseRef` (Task 2)
- Produces (Task 5가 소비): `public List<AuctionCaseRef> parse(String listPageText)`

목록 화면은 상세 화면의 부분집합이라, 목록에서는 "어떤 사건을 클릭해서 상세로 들어가야 하는지"(사건번호+물건번호)만 뽑는다. 나머지 필드는 전부 상세 페이지(Task 4)에서 온다.

- [ ] **Step 1: 실패하는 테스트 작성 (실제 캡처 텍스트 픽스처)**

  아래 텍스트는 2026-07-19 이 세션에서 성남시 수정구 + 상업용및업무용 필터로 실제 브라우저에서 캡처한 목록 화면 원문이다(경매4계 사건 하나가 물건번호 1, 2로 두 번 나오는 실제 케이스 포함).

  ```java
  package com.nextstep.infra.auction;

  import com.nextstep.domain.auction.AuctionCaseRef;
  import java.util.List;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.tuple;

  class AuctionListParserTest {

      private static final String SEONGNAM_LIST_TEXT = """
          전체
          사건번호 물건번호 소재지 및 내역 비고 감정평가액 담당계
          매각기일
          (입찰기간)
          용도 최저매각가격
          (단위:원) 진행상태

          성남지원2025타경51795 선택
          성남지원
          2025타경51795 1
          경기도 성남시 수정구 고등동 616 대왕빌딩주건축물 1동 1층101호
          [집합건물 철근콘크리트구조 36.07㎡]
          지도 783,000,000
          경매4계
          2026.08.03

          상가,오피스텔,근린시설
          131,599,000
          (16%)
          유찰 5회

          성남지원2025타경51925 선택
          성남지원
          2025타경51925 1
          경기도 성남시 수정구 수진동 4762-18 6층 601호
          [집합건물 철근콘크리트조 63.80㎡]
          지도 본건은 '위반건축물'로 등재되어 있어 무단 증축 면적은 향후 원상복구명령, 이행강제금 부과 등에 유의하여 입찰 바람. 257,000,000
          경매6계
          2026.08.10

          상가,오피스텔,근린시설
          88,151,000
          (34%)
          유찰 3회

          성남지원2025타경52088 선택
          성남지원
          2025타경52088 1
          경기도 성남시 수정구 신흥동 4124 신흥역시네마타워 지하1층비1050호
          [집합건물 철근콘크리트구조 7.8413㎡]
          지도 대부분 호실은 오픈상가로 이용되고 있으나, 본건은 유리벽체로 구분되어 사용중이고, 1번과 2번은 벽체의 구분없이 일체로 사용중이며, 판매시설(상호명:베이프크루)로 사용하고 있는 바 경매참여시 참고바람. 195,000,000
          경매4계
          2026.08.03

          상가,오피스텔,근린시설
          95,550,000
          (49%)
          유찰 2회

          성남지원2025타경52088 선택
          성남지원
          2025타경52088 2
          경기도 성남시 수정구 신흥동 4124 신흥역시네마타워 지하1층비1053호
          [집합건물 철근콘크리트구조 7.8413㎡]
          지도 대부분 호실은 오픈상가로 이용되고 있으나, 본건은 유리벽체로 구분되어 사용중이고, 1번과 2번은 벽체의 구분없이 일체로 사용중이며, 판매시설(상호명:베이프크루)로 사용하고 있는 바 경매참여시 참고바람. 195,000,000
          경매4계
          2026.08.03

          상가
          95,550,000
          (49%)
          유찰 2회

          성남지원2025타경52635 선택
          성남지원
          2025타경52635 1
          경기도 성남시 수정구 창곡동 508-2 우성위례타워 1층115호
          [집합건물 철근콘크리트구조 105.98㎡]
          지도 2,630,000,000
          경매7계
          2026.08.03

          상가,오피스텔,근린시설
          902,090,000
          (34%)
          유찰 3회

          총 물건 수 :5건
          """;

      @Test
      void 목록_화면에서_사건번호와_물건번호를_전부_추출한다() {
          AuctionListParser parser = new AuctionListParser();

          List<AuctionCaseRef> refs = parser.parse(SEONGNAM_LIST_TEXT);

          assertThat(refs).extracting(AuctionCaseRef::caseNumber, AuctionCaseRef::itemNumber)
              .containsExactly(
                  tuple("2025타경51795", 1),
                  tuple("2025타경51925", 1),
                  tuple("2025타경52088", 1),
                  tuple("2025타경52088", 2),
                  tuple("2025타경52635", 1)
              );
      }
  }
  ```

- [ ] **Step 2: 테스트 실패 확인**

  ```bash
  mvn test -Dtest=AuctionListParserTest
  ```

  Expected: FAIL — `AuctionListParser` 클래스가 없어 컴파일 에러

- [ ] **Step 3: 파서 구현**

  ```java
  package com.nextstep.infra.auction;

  import com.nextstep.domain.auction.AuctionCaseRef;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  public class AuctionListParser {

      private static final Pattern CASE_DELIMITER =
          Pattern.compile("(?m)^\\S+(\\d{4}타경\\d+) 선택$");
      private static final Pattern ITEM_LINE =
          Pattern.compile("(?m)^\\d{4}타경\\d+ (\\d+)$");

      public List<AuctionCaseRef> parse(String listPageText) {
          List<Integer> matchStarts = new ArrayList<>();
          List<Integer> matchEnds = new ArrayList<>();
          List<String> caseNumbers = new ArrayList<>();

          Matcher delimiterMatcher = CASE_DELIMITER.matcher(listPageText);
          while (delimiterMatcher.find()) {
              matchStarts.add(delimiterMatcher.start());
              matchEnds.add(delimiterMatcher.end());
              caseNumbers.add(delimiterMatcher.group(1));
          }

          List<AuctionCaseRef> refs = new ArrayList<>();
          for (int i = 0; i < matchEnds.size(); i++) {
              int blockStart = matchEnds.get(i);
              int blockEnd = (i + 1 < matchStarts.size())
                  ? matchStarts.get(i + 1)
                  : listPageText.length();
              String block = listPageText.substring(blockStart, blockEnd);

              Matcher itemMatcher = ITEM_LINE.matcher(block);
              if (itemMatcher.find()) {
                  refs.add(new AuctionCaseRef(caseNumbers.get(i), Integer.parseInt(itemMatcher.group(1))));
              }
          }
          return refs;
      }
  }
  ```

- [ ] **Step 4: 테스트 통과 확인**

  ```bash
  mvn test -Dtest=AuctionListParserTest
  ```

  Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 커밋**

  ```bash
  git add src/main/java/com/nextstep/infra/auction/AuctionListParser.java src/test/java/com/nextstep/infra/auction/AuctionListParserTest.java
  git commit -m "feat: add auction list-page parser"
  ```

---

### Task 4: AuctionDetailParser — 상세 화면에서 전체 사건 데이터 추출

**Files:**
- Create: `src/main/java/com/nextstep/infra/auction/AuctionDetailParser.java`
- Test: `src/test/java/com/nextstep/infra/auction/AuctionDetailParserTest.java`

**Interfaces:**
- Consumes: `AuctionCase`, `AuctionScheduleEntry` (Task 2)
- Produces (Task 5가 소비): `public AuctionCase parse(String caseNumber, int itemNumber, String detailPageText)`

- [ ] **Step 1: 실패하는 테스트 작성 (2025타경51795 상세 화면 실제 캡처 텍스트)**

  ```java
  package com.nextstep.infra.auction;

  import com.nextstep.domain.auction.AuctionCase;
  import java.math.BigDecimal;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.tuple;

  class AuctionDetailParserTest {

      private static final String CASE_51795_DETAIL_TEXT = """
          법원성남지원
          사건번호2025타경51795
          물건기본정보
          사건번호,물건번호,물건종류,감정평가액,최저매각가격 (매수신청보증금),입찰방법,매각기일,물건비고,담당 을(를) 나타낸 표
          사건번호
          2025타경51795전자
          물건번호
          1
          물건종류
          상가,오피스텔,근린시설

          감정평가액
          783,000,000원
          최저매각가격
          (매수신청보증금)
          131,599,000원
          (13,159,900원)
          입찰방법
          기일입찰

          예정매각기일
          2026.08.03 10:00 제3별관 1층 제5호법정

          물건비고

          목록1 소재지
          (근린생활시설) 경기도 성남시 수정구 고등동 616 대왕빌딩주건축물 1동 1층101호

          담당
          수원지방법원 성남지원| 경매4계
          사건접수,경매개시일,배당요구종기,청구금액 을(를) 나타낸 표
          사건접수
          2025.03.14
          경매개시일
          2025.03.15

          배당요구종기
          2025.05.19
          청구금액
          461,495,221원
          기일내역
          기일,기일종류,기일장소,최저매각가격,기일결과 을(를) 나타낸 표
          기일 기일종류 기일장소 최저매각가격 기일결과
          2026.02.09 (10:00) 매각기일 제3별관 1층 제5호법정 783,000,000원 유찰
          2026.03.16 (10:00) 매각기일 제3별관 1층 제5호법정 548,100,000원 유찰
          2026.04.20 (10:00) 매각기일 제3별관 1층 제5호법정 383,670,000원 유찰
          2026.05.22 (10:00) 매각기일 제3별관 1층 제5호법정 268,569,000원 유찰
          2026.06.29 (10:00) 매각기일 제3별관 1층 제5호법정 187,998,000원 유찰
          2026.08.03 (10:00) 매각기일 제3별관 1층 제5호법정 131,599,000원
          2026.08.10 (16:00) 매각결정기일 제3별관 1층 제5호법정
          감정평가요항표 요약
          1. 구분건물감정평가요항표 요약 - 철근콘크리트구조 건물 내 상업용 구분건물로 승강기 등 이용 편리함.
          인근매각물건사례
          유의사항
          """;

      @Test
      void 상세_화면에서_전체_필드를_추출한다() {
          AuctionDetailParser parser = new AuctionDetailParser();

          AuctionCase auctionCase = parser.parse("2025타경51795", 1, CASE_51795_DETAIL_TEXT);

          assertThat(auctionCase.caseNumber()).isEqualTo("2025타경51795");
          assertThat(auctionCase.itemNumber()).isEqualTo(1);
          assertThat(auctionCase.court()).isEqualTo("수원지방법원 성남지원");
          assertThat(auctionCase.divisionName()).isEqualTo("경매4계");
          assertThat(auctionCase.propertyType()).isEqualTo("상가,오피스텔,근린시설");
          assertThat(auctionCase.jibunAddress())
              .isEqualTo("(근린생활시설) 경기도 성남시 수정구 고등동 616 대왕빌딩주건축물 1동 1층101호");
          assertThat(auctionCase.appraisalValueKrw()).isEqualByComparingTo(new BigDecimal("783000000"));
          assertThat(auctionCase.minimumSalePriceKrw()).isEqualByComparingTo(new BigDecimal("131599000"));
          assertThat(auctionCase.bidDepositKrw()).isEqualByComparingTo(new BigDecimal("13159900"));
          assertThat(auctionCase.biddingMethod()).isEqualTo("기일입찰");
          assertThat(auctionCase.saleDate()).isEqualTo("2026.08.03 10:00 제3별관 1층 제5호법정");
          assertThat(auctionCase.filedDate()).isEqualTo("2025.03.14");
          assertThat(auctionCase.auctionStartDate()).isEqualTo("2025.03.15");
          assertThat(auctionCase.claimDeadline()).isEqualTo("2025.05.19");
          assertThat(auctionCase.claimAmountKrw()).isEqualByComparingTo(new BigDecimal("461495221"));
          assertThat(auctionCase.appraisalSummary()).startsWith("감정평가요항표 요약");
          assertThat(auctionCase.appraisalSummary()).doesNotContain("인근매각물건사례");

          assertThat(auctionCase.scheduleHistory())
              .extracting(e -> e.scheduleDate(), e -> e.scheduleType(), e -> e.result())
              .containsExactly(
                  tuple("2026.02.09", "매각기일", "유찰"),
                  tuple("2026.03.16", "매각기일", "유찰"),
                  tuple("2026.04.20", "매각기일", "유찰"),
                  tuple("2026.05.22", "매각기일", "유찰"),
                  tuple("2026.06.29", "매각기일", "유찰"),
                  tuple("2026.08.03", "매각기일", null)
              );
          assertThat(auctionCase.scheduleHistory().get(0).minimumPriceKrw())
              .isEqualByComparingTo(new BigDecimal("783000000"));
          assertThat(auctionCase.scheduleHistory().get(0).location())
              .isEqualTo("제3별관 1층 제5호법정");
          assertThat(auctionCase.scheduleHistory().get(5).minimumPriceKrw())
              .isEqualByComparingTo(new BigDecimal("131599000"));
      }
  }
  ```

  참고: 원문에는 기일이 7줄 있지만 매칭되는 것은 6개뿐이다. 마지막 7번째 줄("2026.08.10 (16:00) 매각결정기일 제3별관 1층 제5호법정")은 가격("...원") 자체가 없는 행이라 `SCHEDULE_ROW` 정규식이 애초에 매칭하지 않는다(가격 캡처 그룹이 필수라서) — 그래서 스케줄 항목으로 만들어지지 않는 것이 맞는 동작이다. 반면 6번째 줄은 가격은 있고 결과(유찰 등)만 아직 없는 "예정 기일"이라, 정규식은 매칭하되 `result` 그룹만 `null`이 된다 — 이것도 다가올 매각기일을 아는 게 유용하므로 의도적으로 유지한다.

- [ ] **Step 2: 테스트 실패 확인**

  ```bash
  mvn test -Dtest=AuctionDetailParserTest
  ```

  Expected: FAIL — `AuctionDetailParser` 클래스가 없어 컴파일 에러

- [ ] **Step 3: 파서 구현**

  ```java
  package com.nextstep.infra.auction;

  import com.nextstep.domain.auction.AuctionCase;
  import com.nextstep.domain.auction.AuctionScheduleEntry;
  import java.math.BigDecimal;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  public class AuctionDetailParser {

      private static final Pattern MINIMUM_PRICE_BLOCK = Pattern.compile(
          "최저매각가격\\s*\\n\\(매수신청보증금\\)\\s*\\n([\\d,]+)원\\s*\\n\\(([\\d,]+)원\\)"
      );
      private static final Pattern JIBUN_ADDRESS = Pattern.compile("목록\\d+ 소재지\\s*\\n(.+?)\\s*\\n");
      private static final Pattern SCHEDULE_ROW = Pattern.compile(
          "(?m)^(\\d{4}\\.\\d{2}\\.\\d{2}) \\((\\d{2}:\\d{2})\\) (매각기일|매각결정기일) (.+?) ([\\d,]+)원(?:\\s+(\\S+))?$"
      );

      public AuctionCase parse(String caseNumber, int itemNumber, String detailPageText) {
          String[] courtParts = splitCourt(firstLineAfter(detailPageText, "담당"));
          BigDecimal[] priceAndDeposit = parseMinimumPriceAndDeposit(detailPageText);

          return new AuctionCase(
              caseNumber,
              itemNumber,
              courtParts[0],
              courtParts[1],
              firstLineAfter(detailPageText, "물건종류"),
              extractJibunAddress(detailPageText),
              parseKrwAmount(firstLineAfter(detailPageText, "감정평가액")),
              priceAndDeposit[0],
              priceAndDeposit[1],
              firstLineAfter(detailPageText, "입찰방법"),
              firstLineAfter(detailPageText, "예정매각기일"),
              firstLineAfter(detailPageText, "사건접수"),
              firstLineAfter(detailPageText, "경매개시일"),
              firstLineAfter(detailPageText, "배당요구종기"),
              parseKrwAmount(firstLineAfter(detailPageText, "청구금액")),
              extractAppraisalSummary(detailPageText),
              parseScheduleHistory(detailPageText)
          );
      }

      private String[] splitCourt(String value) {
          if (value == null) {
              return new String[] {null, null};
          }
          String[] parts = value.split("\\|", 2);
          String court = parts[0].trim();
          String division = parts.length > 1 ? parts[1].trim() : null;
          return new String[] {court, division};
      }

      private String extractJibunAddress(String text) {
          Matcher matcher = JIBUN_ADDRESS.matcher(text);
          return matcher.find() ? matcher.group(1).trim() : null;
      }

      private BigDecimal[] parseMinimumPriceAndDeposit(String text) {
          Matcher matcher = MINIMUM_PRICE_BLOCK.matcher(text);
          if (!matcher.find()) {
              return new BigDecimal[] {null, null};
          }
          return new BigDecimal[] {parseAmount(matcher.group(1)), parseAmount(matcher.group(2))};
      }

      private String extractAppraisalSummary(String text) {
          int start = text.indexOf("감정평가요항표 요약");
          int end = text.indexOf("인근매각물건사례");
          if (start == -1 || end == -1 || end <= start) {
              return null;
          }
          return text.substring(start, end).trim();
      }

      private List<AuctionScheduleEntry> parseScheduleHistory(String text) {
          List<AuctionScheduleEntry> entries = new ArrayList<>();
          Matcher matcher = SCHEDULE_ROW.matcher(text);
          while (matcher.find()) {
              entries.add(new AuctionScheduleEntry(
                  matcher.group(1),
                  matcher.group(2),
                  matcher.group(3),
                  matcher.group(4).trim(),
                  parseAmount(matcher.group(5)),
                  matcher.group(6)
              ));
          }
          return entries;
      }

      private String firstLineAfter(String text, String label) {
          Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(label) + "\\s*$\\n+(.+)$");
          Matcher matcher = pattern.matcher(text);
          return matcher.find() ? matcher.group(1).trim() : null;
      }

      private BigDecimal parseKrwAmount(String value) {
          if (value == null) {
              return null;
          }
          return parseAmount(value.replace("원", ""));
      }

      private BigDecimal parseAmount(String digitsWithCommas) {
          if (digitsWithCommas == null || digitsWithCommas.isBlank()) {
              return null;
          }
          return new BigDecimal(digitsWithCommas.replace(",", "").trim());
      }
  }
  ```

- [ ] **Step 4: 테스트 통과 확인**

  ```bash
  mvn test -Dtest=AuctionDetailParserTest
  ```

  Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 커밋**

  ```bash
  git add src/main/java/com/nextstep/infra/auction/AuctionDetailParser.java src/test/java/com/nextstep/infra/auction/AuctionDetailParserTest.java
  git commit -m "feat: add auction detail-page parser"
  ```

---

### Task 5: CourtAuctionCollector — Playwright로 실제 화면 구동

**Files:**
- Create: `src/main/java/com/nextstep/infra/auction/CourtAuctionCollector.java`
- Test: `src/test/java/com/nextstep/infra/auction/CourtAuctionCollectorManualCheck.java` (실사이트 대상, 수동 실행 전용)

**Interfaces:**
- Consumes: `AuctionListParser.parse(String)` (Task 3), `AuctionDetailParser.parse(String, int, String)` (Task 4), `AuctionCaseRef` (Task 2)
- Produces (Task 6이 소비): `public List<AuctionCase> collectSeongnamSujeongGu()`

이 클래스는 이번 세션에 claude-in-chrome으로 직접 확인한 실제 조작 순서를 그대로 Playwright API로 옮긴 것이다: 소재지(지번주소) 탭 선택 → 시/도=경기도 → 시/군/구=성남시 수정구 → 용도 대분류=건물 → 중분류=상업용및업무용 → 검색 → 목록에서 각 사건을 클릭해 상세 텍스트 획득. WebSquare가 접근성 트리(ARIA role/name)를 정상적으로 노출하는 것을 이번 세션에 `find` 도구로 이미 확인했으므로, CSS 셀렉터 대신 Playwright의 `getByRole`/`getByText`(접근성 기반 로케이터)를 쓴다 — 페이지의 실제 HTML id/class를 이번 세션에 직접 캡처하지 못했기 때문에, 캡처하지 못한 셀렉터를 지어내는 대신 이미 검증된 접근성 이름만 사용한다.

**"시/군/구" 콤보박스가 페이지에 2개 존재하는 것으로 확인됨(이번 세션 실측)** — 지번주소 탭용과 다른 탭용. `.first()`로 첫 번째(지번주소 탭 활성화 후 노출되는 것)를 선택한다. 만약 실행 시점에 순서가 바뀌어 있으면(WebSquare가 탭 전환 후 DOM 순서를 재배치할 수 있음) 이 부분이 가장 먼저 깨질 지점이므로, Step 6의 수동 스모크에서 반드시 눈으로 확인한다.

- [ ] **Step 1: pom.xml의 Playwright 의존성 스코프를 compile로 변경**

  Task 1에서 `<scope>test</scope>`로 넣었던 것을 이 태스크부터는 `src/main`에서도 써야 하므로 제거한다(Playwright 기본 스코프는 compile):

  ```xml
    <dependency>
      <groupId>com.microsoft.playwright</groupId>
      <artifactId>playwright</artifactId>
      <version>1.52.0</version>
    </dependency>
  ```

- [ ] **Step 2: 컴파일 확인**

  ```bash
  mvn compile
  ```

  Expected: `BUILD SUCCESS`

- [ ] **Step 3: CourtAuctionCollector 구현**

  ```java
  package com.nextstep.infra.auction;

  import com.microsoft.playwright.Browser;
  import com.microsoft.playwright.BrowserType;
  import com.microsoft.playwright.Page;
  import com.microsoft.playwright.Playwright;
  import com.microsoft.playwright.options.AriaRole;
  import com.nextstep.domain.auction.AuctionCase;
  import com.nextstep.domain.auction.AuctionCaseRef;
  import java.util.ArrayList;
  import java.util.List;

  public class CourtAuctionCollector {

      private static final String SEARCH_URL =
          "https://www.courtauction.go.kr/pgj/index.on?w2xPath=/pgj/ui/pgj100/PGJ157M00.xml";

      private final AuctionListParser listParser = new AuctionListParser();
      private final AuctionDetailParser detailParser = new AuctionDetailParser();

      public List<AuctionCase> collectSeongnamSujeongGu() {
          List<AuctionCase> results = new ArrayList<>();

          try (Playwright playwright = Playwright.create()) {
              Browser browser = playwright.chromium()
                  .launch(new BrowserType.LaunchOptions().setHeadless(true));
              Page page = browser.newPage();

              page.navigate(SEARCH_URL);
              page.getByText("소재지(지번주소)").click();
              page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("시/도"))
                  .selectOption("경기도");
              page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("시/군/구"))
                  .first()
                  .selectOption("성남시 수정구");
              page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("대분류"))
                  .selectOption("건물");
              page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("중분류"))
                  .selectOption("상업용및업무용");
              page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("검색")).click();
              page.waitForLoadState();

              String listText = page.innerText("body");
              List<AuctionCaseRef> refs = listParser.parse(listText);

              for (AuctionCaseRef ref : refs) {
                  page.getByText(ref.caseNumber() + " 선택").first().click();
                  page.waitForLoadState();
                  String detailText = page.innerText("body");
                  results.add(detailParser.parse(ref.caseNumber(), ref.itemNumber(), detailText));
                  page.goBack();
                  page.waitForLoadState();
              }

              browser.close();
          }

          return results;
      }
  }
  ```

  참고: `page.getByText(ref.caseNumber() + " 선택")`이 물건번호 1과 2를 가진 같은 사건번호에 대해 목록에 두 번 나타나는 경우(예: 2025타경52088), `.first()`만으로는 두 번째 물건을 클릭할 수 없다는 한계가 있다. 이번 스파이크 범위에서는 이 한계를 알고 넘어간다 — 실측 데이터 기준 이런 케이스가 드물고(5건 중 1쌍), 완전히 고치려면 목록 화면의 행 순서와 Playwright locator의 nth 인덱스를 매칭하는 추가 로직이 필요한데 이는 실제 DOM 구조를 다시 캡처해야 확정할 수 있다. 다음 세션에서 실제 DOM을 캡처하면 `nth(rowIndex)`로 교체한다.

- [ ] **Step 4: 수동 스모크 테스트 작성 (실사이트 대상)**

  ```java
  package com.nextstep.infra.auction;

  import com.nextstep.domain.auction.AuctionCase;
  import java.util.List;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;

  class CourtAuctionCollectorManualCheck {

      @Test
      void 성남시_수정구_상업용_경매사건을_실제로_수집한다() {
          CourtAuctionCollector collector = new CourtAuctionCollector();

          List<AuctionCase> cases = collector.collectSeongnamSujeongGu();

          assertThat(cases).isNotEmpty();
          cases.forEach(c -> System.out.println(c.caseNumber() + " " + c.itemNumber() + " " + c.jibunAddress()));
      }
  }
  ```

- [ ] **Step 5: 수동 실행 (개발자가 직접, 실제 정부 사이트를 침 — CI에서 자동 실행되지 않음)**

  ```bash
  mvn test -Dtest=CourtAuctionCollectorManualCheck
  ```

  Expected: 콘솔에 사건번호·물건번호·지번주소가 출력됨. Task 3/4의 셀렉터 이름(예: "시/군/구" 콤보박스 접근성 이름)이 실제 사이트와 다르면 여기서 실패한다 — 실패 시 claude-in-chrome이나 Playwright Inspector(`PWDEBUG=1`)로 실제 접근성 이름을 다시 확인해 Step 3을 수정한다.

- [ ] **Step 6: 커밋**

  ```bash
  git add pom.xml src/main/java/com/nextstep/infra/auction/CourtAuctionCollector.java src/test/java/com/nextstep/infra/auction/CourtAuctionCollectorManualCheck.java
  git commit -m "feat: add Playwright-driven court auction collector"
  ```

---

### Task 6: JPA 영속성 + 수동 트리거 서비스 (스케줄링·API 연결 없음)

**Files:**
- Create: `src/main/java/com/nextstep/infra/persistence/AuctionCaseEntity.java`
- Create: `src/main/java/com/nextstep/infra/persistence/AuctionScheduleEntryEntity.java`
- Create: `src/main/java/com/nextstep/infra/persistence/AuctionCaseRepository.java`
- Create: `src/main/java/com/nextstep/application/auction/AuctionCollectionService.java`
- Test: `src/test/java/com/nextstep/application/auction/AuctionCollectionServiceTest.java`

**Interfaces:**
- Consumes: `AuctionCase`, `AuctionScheduleEntry` (Task 2), `CourtAuctionCollector.collectSeongnamSujeongGu()` (Task 5)
- Produces: `AuctionCollectionService.collectAndPersist()` — **어디에도 자동 호출되지 않음**. 개발자가 테스트나 REPL에서 직접 호출해야 한다.

- [ ] **Step 1: AuctionScheduleEntryEntity 작성**

  ```java
  package com.nextstep.infra.persistence;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.GeneratedValue;
  import jakarta.persistence.GenerationType;
  import jakarta.persistence.Id;
  import jakarta.persistence.ManyToOne;
  import jakarta.persistence.Table;
  import java.math.BigDecimal;

  @Entity
  @Table(name = "auction_schedule_entry")
  public class AuctionScheduleEntryEntity {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @ManyToOne
      private AuctionCaseEntity auctionCase;

      private String scheduleDate;
      private String scheduleTime;
      private String scheduleType;
      @Column(length = 300)
      private String location;
      private BigDecimal minimumPriceKrw;
      private String result;

      protected AuctionScheduleEntryEntity() {
      }

      public AuctionScheduleEntryEntity(AuctionCaseEntity auctionCase, String scheduleDate, String scheduleTime,
                                         String scheduleType, String location, BigDecimal minimumPriceKrw,
                                         String result) {
          this.auctionCase = auctionCase;
          this.scheduleDate = scheduleDate;
          this.scheduleTime = scheduleTime;
          this.scheduleType = scheduleType;
          this.location = location;
          this.minimumPriceKrw = minimumPriceKrw;
          this.result = result;
      }

      public Long getId() { return id; }
      public String getScheduleDate() { return scheduleDate; }
      public String getScheduleTime() { return scheduleTime; }
      public String getScheduleType() { return scheduleType; }
      public String getLocation() { return location; }
      public BigDecimal getMinimumPriceKrw() { return minimumPriceKrw; }
      public String getResult() { return result; }
  }
  ```

- [ ] **Step 2: AuctionCaseEntity 작성**

  ```java
  package com.nextstep.infra.persistence;

  import jakarta.persistence.CascadeType;
  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.GeneratedValue;
  import jakarta.persistence.GenerationType;
  import jakarta.persistence.Id;
  import jakarta.persistence.OneToMany;
  import jakarta.persistence.Table;
  import java.math.BigDecimal;
  import java.util.ArrayList;
  import java.util.List;

  @Entity
  @Table(name = "auction_case")
  public class AuctionCaseEntity {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @Column(nullable = false)
      private String caseNumber;
      @Column(nullable = false)
      private Integer itemNumber;
      private String court;
      private String divisionName;
      private String propertyType;
      @Column(length = 300)
      private String jibunAddress;
      private BigDecimal appraisalValueKrw;
      private BigDecimal minimumSalePriceKrw;
      private BigDecimal bidDepositKrw;
      private String biddingMethod;
      private String saleDate;
      private String filedDate;
      private String auctionStartDate;
      private String claimDeadline;
      private BigDecimal claimAmountKrw;
      @Column(length = 4000)
      private String appraisalSummary;

      @OneToMany(mappedBy = "auctionCase", cascade = CascadeType.ALL, orphanRemoval = true)
      private List<AuctionScheduleEntryEntity> scheduleHistory = new ArrayList<>();

      protected AuctionCaseEntity() {
      }

      public AuctionCaseEntity(String caseNumber, Integer itemNumber, String court, String divisionName,
                                String propertyType, String jibunAddress, BigDecimal appraisalValueKrw,
                                BigDecimal minimumSalePriceKrw, BigDecimal bidDepositKrw, String biddingMethod,
                                String saleDate, String filedDate, String auctionStartDate, String claimDeadline,
                                BigDecimal claimAmountKrw, String appraisalSummary) {
          this.caseNumber = caseNumber;
          this.itemNumber = itemNumber;
          this.court = court;
          this.divisionName = divisionName;
          this.propertyType = propertyType;
          this.jibunAddress = jibunAddress;
          this.appraisalValueKrw = appraisalValueKrw;
          this.minimumSalePriceKrw = minimumSalePriceKrw;
          this.bidDepositKrw = bidDepositKrw;
          this.biddingMethod = biddingMethod;
          this.saleDate = saleDate;
          this.filedDate = filedDate;
          this.auctionStartDate = auctionStartDate;
          this.claimDeadline = claimDeadline;
          this.claimAmountKrw = claimAmountKrw;
          this.appraisalSummary = appraisalSummary;
      }

      public void addScheduleEntry(AuctionScheduleEntryEntity entry) {
          scheduleHistory.add(entry);
      }

      public Long getId() { return id; }
      public String getCaseNumber() { return caseNumber; }
      public Integer getItemNumber() { return itemNumber; }
      public String getCourt() { return court; }
      public String getDivisionName() { return divisionName; }
      public String getPropertyType() { return propertyType; }
      public String getJibunAddress() { return jibunAddress; }
      public BigDecimal getAppraisalValueKrw() { return appraisalValueKrw; }
      public BigDecimal getMinimumSalePriceKrw() { return minimumSalePriceKrw; }
      public BigDecimal getBidDepositKrw() { return bidDepositKrw; }
      public String getBiddingMethod() { return biddingMethod; }
      public String getSaleDate() { return saleDate; }
      public String getFiledDate() { return filedDate; }
      public String getAuctionStartDate() { return auctionStartDate; }
      public String getClaimDeadline() { return claimDeadline; }
      public BigDecimal getClaimAmountKrw() { return claimAmountKrw; }
      public String getAppraisalSummary() { return appraisalSummary; }
      public List<AuctionScheduleEntryEntity> getScheduleHistory() { return scheduleHistory; }
  }
  ```

- [ ] **Step 3: AuctionCaseRepository 작성**

  ```java
  package com.nextstep.infra.persistence;

  import org.springframework.data.jpa.repository.JpaRepository;

  public interface AuctionCaseRepository extends JpaRepository<AuctionCaseEntity, Long> {
  }
  ```

- [ ] **Step 4: 실패하는 테스트 작성 (서비스가 수집기 결과를 엔티티로 저장하는지)**

  ```java
  package com.nextstep.application.auction;

  import com.nextstep.domain.auction.AuctionCase;
  import com.nextstep.domain.auction.AuctionScheduleEntry;
  import com.nextstep.infra.persistence.AuctionCaseEntity;
  import com.nextstep.infra.persistence.AuctionCaseRepository;
  import java.math.BigDecimal;
  import java.util.List;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

  import static org.assertj.core.api.Assertions.assertThat;

  @DataJpaTest
  class AuctionCollectionServiceTest {

      @Autowired
      AuctionCaseRepository repository;

      @Test
      void 수집된_사건을_스케줄내역과_함께_저장한다() {
          AuctionCase collected = new AuctionCase(
              "2025타경51795", 1, "수원지방법원 성남지원", "경매4계",
              "상가,오피스텔,근린시설", "경기도 성남시 수정구 고등동 616",
              new BigDecimal("783000000"), new BigDecimal("131599000"), new BigDecimal("13159900"),
              "기일입찰", "2026.08.03", "2025.03.14", "2025.03.15", "2025.05.19",
              new BigDecimal("461495221"), "감정평가요항표 요약 ...",
              List.of(new AuctionScheduleEntry("2026.02.09", "10:00", "매각기일",
                  "제3별관 1층 제5호법정", new BigDecimal("783000000"), "유찰"))
          );
          AuctionCollectionService service = new AuctionCollectionService(() -> List.of(collected), repository);

          service.collectAndPersist();

          List<AuctionCaseEntity> saved = repository.findAll();
          assertThat(saved).hasSize(1);
          assertThat(saved.get(0).getCaseNumber()).isEqualTo("2025타경51795");
          assertThat(saved.get(0).getScheduleHistory()).hasSize(1);
          assertThat(saved.get(0).getScheduleHistory().get(0).getResult()).isEqualTo("유찰");
      }
  }
  ```

  테스트가 `CourtAuctionCollector`(실사이트 호출)를 직접 쓰지 않도록, 서비스 생성자는 `Supplier<List<AuctionCase>>`를 받는다 — 실제 `main`에서 부팅할 때만 `collector::collectSeongnamSujeongGu`를 넘긴다.

- [ ] **Step 5: 테스트 실패 확인**

  ```bash
  mvn test -Dtest=AuctionCollectionServiceTest
  ```

  Expected: FAIL — `AuctionCollectionService` 클래스가 없어 컴파일 에러

- [ ] **Step 6: AuctionCollectionService 구현**

  ```java
  package com.nextstep.application.auction;

  import com.nextstep.domain.auction.AuctionCase;
  import com.nextstep.domain.auction.AuctionScheduleEntry;
  import com.nextstep.infra.persistence.AuctionCaseEntity;
  import com.nextstep.infra.persistence.AuctionCaseRepository;
  import com.nextstep.infra.persistence.AuctionScheduleEntryEntity;
  import java.util.List;
  import java.util.function.Supplier;

  /**
   * 개발용 스파이크 전용. 어떤 컨트롤러에도 연결하지 말 것 — 개발자가 직접 호출해야만 실행된다.
   */
  public class AuctionCollectionService {

      private final Supplier<List<AuctionCase>> collector;
      private final AuctionCaseRepository repository;

      public AuctionCollectionService(Supplier<List<AuctionCase>> collector, AuctionCaseRepository repository) {
          this.collector = collector;
          this.repository = repository;
      }

      public void collectAndPersist() {
          for (AuctionCase auctionCase : collector.get()) {
              AuctionCaseEntity entity = new AuctionCaseEntity(
                  auctionCase.caseNumber(), auctionCase.itemNumber(), auctionCase.court(),
                  auctionCase.divisionName(), auctionCase.propertyType(), auctionCase.jibunAddress(),
                  auctionCase.appraisalValueKrw(), auctionCase.minimumSalePriceKrw(), auctionCase.bidDepositKrw(),
                  auctionCase.biddingMethod(), auctionCase.saleDate(), auctionCase.filedDate(),
                  auctionCase.auctionStartDate(), auctionCase.claimDeadline(), auctionCase.claimAmountKrw(),
                  auctionCase.appraisalSummary()
              );
              for (AuctionScheduleEntry entry : auctionCase.scheduleHistory()) {
                  entity.addScheduleEntry(new AuctionScheduleEntryEntity(
                      entity, entry.scheduleDate(), entry.scheduleTime(), entry.scheduleType(),
                      entry.location(), entry.minimumPriceKrw(), entry.result()
                  ));
              }
              repository.save(entity);
          }
      }
  }
  ```

  참고: `@Component`를 붙이지 않았다 — Spring이 자동으로 빈을 만들어 애플리케이션 컨텍스트에 올리지 않도록 하기 위함이다(우연히라도 다른 곳에서 주입받아 호출될 여지를 없앤다). 실제로 돌려볼 때는 개발자가 `@DataJpaTest`나 `@SpringBootTest` 안에서 `new AuctionCollectionService(new CourtAuctionCollector()::collectSeongnamSujeongGu, repository)`로 직접 생성해 호출한다.

- [ ] **Step 7: 테스트 통과 확인**

  ```bash
  mvn test -Dtest=AuctionCollectionServiceTest
  ```

  Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 8: 전체 테스트 스위트가 여전히 통과하는지 확인 (실사이트 호출 테스트는 자동 제외됨을 재확인)**

  ```bash
  mvn test
  ```

  Expected: `BUILD SUCCESS`, 출력에 `PlaywrightSmokeCheck`/`CourtAuctionCollectorManualCheck` 언급 없음

- [ ] **Step 9: 커밋**

  ```bash
  git add src/main/java/com/nextstep/infra/persistence/AuctionCaseEntity.java src/main/java/com/nextstep/infra/persistence/AuctionScheduleEntryEntity.java src/main/java/com/nextstep/infra/persistence/AuctionCaseRepository.java src/main/java/com/nextstep/application/auction/AuctionCollectionService.java src/test/java/com/nextstep/application/auction/AuctionCollectionServiceTest.java
  git commit -m "feat: add JPA persistence + manually-triggered auction collection service"
  ```

---

## 완료 후 남는 것 (범위 밖, 의도적으로 안 함)

- **REST 엔드포인트 없음**: `spec/api-spec.md`에 손대지 않았다. 프론트에서 이 데이터를 볼 방법이 없다 — 의도적이다.
- **스케줄링 없음**: 매번 `AuctionCollectionService.collectAndPersist()`를 개발자가 직접 호출해야 한다.
- **CODEF/법원행정처 동의 확인 전까지 이 이상 진행하지 않는다.** 동의가 확인되면 별도 브레인스토밍으로 (a) REST 노출 여부·스코프, (b) 입찰의향 확인 UI 게이팅, (c) 별도 서비스 분리 여부를 다시 논의한다 — `docs/superpowers/specs/2026-07-18-auction-data-integration-design.md`의 "병행 가능한 작업" 섹션 참고.
