# Task 5 Report: JPA 엔티티 + 리포지토리

**Status:** DONE

## 요약

모든 스텝 완료: TDD 순서대로 실패하는 테스트 → 엔티티/리포지토리 6개 구현 → 테스트 3개 통과 → git 커밋.

## 진행 내역

### Step 1-2: 실패하는 테스트 작성 및 확인
- `PersistenceSmokeTest.java` 작성 (테스트 메서드 3개)
- 예상대로 컴파일 에러 확인 (SiteEntity, UnitEntity, TenancyEntity, 그들의 repository 미정의)

### Step 3-5: 엔티티 & 리포지토리 구현
- `SiteEntity` (site 테이블 매핑): pnu(PK), jibunAddress, roadAddress, 좌표 등
- `SiteJpaRepository`: 지번/도로명주소 검색 쿼리 포함
- `UnitEntity` (unit 테이블 매핑): unitId(PK), sitePnu(FK), label, locationSource
- `UnitJpaRepository`: 자리 PNU로 물건 검색
- `TenancyEntity` (tenancy_record 테이블 매핑): id(PK), unitId(FK), 사업명, 카테고리, 인허가/폐업일자 등
- `TenancyJpaRepository`: unitId로 이력 검색, 복수 unitId 검색 지원

**설계 원칙 준수:**
- @ManyToOne/@OneToMany 연관관계 매핑 없음 → FK를 평범한 String/Long 컬럼으로만 다룸 (N+1 방지)
- 모든 엔티티는 protected no-arg constructor 포함
- getter만 public, setter 없음 (불변 설계)

### Step 6: 테스트 재실행
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

세 테스트 모두 성공:
1. `시드_데이터가_전부_로드된다` — site 6행, unit 6행, tenancy_record 8행 확인
2. `자리로_물건을_조회한다` — PNU로 unit 검색, unitId 매칭 확인
3. `물건으로_이력을_조회하면_두_건이_나온다` — unitId로 tenancy 검색, 2건 확인

### Step 7: 커밋
```
[main 25c668b] feat: add JPA entities and repositories
 7 files changed, 151 insertions(+)
```

## 브리프와의 차이점

없음. 브리프의 모든 코드를 그대로 적용했고, 설계 원칙(FK-as-String, no ORM relationships)도 준수했다.

## 검증 체크리스트

- [x] 테스트 3개 작성
- [x] 엔티티 3개 구현 (schema.sql 스키마와 매핑)
- [x] 리포지토리 3개 구현 (필요한 query method 포함)
- [x] H2 인메모리 DB에서 schema.sql/data.sql 자동 로드 확인
- [x] 시드 데이터 counts 정확 (site 6, unit 6, tenancy_record 8)
- [x] 조회 메서드 동작 확인 (findBySitePnu, findByUnitId)
- [x] git commit 완료

## 마이그레이션 관계

이 계층은 순수 JPA 인터페이스로만 구성되어 있어 향후 도메인 모델 계층과의 연결이 깔끔하다. 리포지토리 구현체는 Spring Data가 프록시로 자동 생성하므로, 도메인 서비스에서는 interface만 @Autowired하면 된다.

---

**Commit Hash:** 25c668b  
**Test Result:** 3/3 PASS  
**Timestamp:** 2026-07-10T08:00+09:00
