# Task 1: Spring Boot 백엔드 스캐폴딩 — 완료 보고

**상태:** DONE

**커밋 해시:** `4699bee`

**빌드 결과:** SUCCESS (`mvn -q compile` 완료, quiet mode)

## 작업 내용

Task 1 브리프의 모든 Step을 순서대로 완료했습니다.

### Step 1: pom.xml 작성
- Maven 기반 Spring Boot 3.3.4 프로젝트 설정
- 의존성: spring-boot-starter-web, data-jpa, cache, caffeine, h2, test
- Java 21 지정

### Step 2: .gitignore 작성
- 표준 Spring Boot 무시 패턴 (target/, data/, .idea/, *.iml)
- application-local.yml 제외

### Step 3: application.yml 작성
- H2 파일 기반 데이터소스 (./data/nextstep)
- Caffeine 캐시 설정 (sameCategoryNearbyCount, 10m TTL)
- 상가 API 베이스URL 및 서비스키 환경변수
- 서버 포트 8080

### Step 4: 메인 클래스 작성
- com.nextstep.NextstepApplication
- @SpringBootApplication, @EnableCaching, @ConfigurationPropertiesScan 애노테이션

### Step 5: 빌드 확인
```
mvn -q compile
```
결과: 성공, 출력 없음 (quiet mode)

### Step 6: Git 초기화 + 커밋
```
git init
git add pom.xml .gitignore src
git commit -m "chore: scaffold Spring Boot project"
```
결과: 커밋 4699bee 생성

## 파일 목록

생성된 파일:
- `server/pom.xml`
- `server/.gitignore`
- `server/src/main/resources/application.yml`
- `server/src/main/java/com/nextstep/NextstepApplication.java`

## 주의사항

윈도우 환경에서 git 권한 관련 경고가 발생했으나, `git config --global --add safe.directory`로 해결함. 커밋 과정에서 LF→CRLF 경고는 정상 (윈도우 환경).

---

**완료 일시:** 2026-07-10  
**작업자:** Claude Code
