### Task 5: JPA 엔티티 + 리포지토리

**Files:**
- Create: `server/src/main/java/com/nextstep/infra/persistence/SiteEntity.java`
- Create: `server/src/main/java/com/nextstep/infra/persistence/SiteJpaRepository.java`
- Create: `server/src/main/java/com/nextstep/infra/persistence/UnitEntity.java`
- Create: `server/src/main/java/com/nextstep/infra/persistence/UnitJpaRepository.java`
- Create: `server/src/main/java/com/nextstep/infra/persistence/TenancyEntity.java`
- Create: `server/src/main/java/com/nextstep/infra/persistence/TenancyJpaRepository.java`
- Test: `server/src/test/java/com/nextstep/infra/persistence/PersistenceSmokeTest.java`

**Interfaces:**
- Produces: `SiteJpaRepository extends JpaRepository<SiteEntity, String>` + `searchByAddress(String q)`. `UnitJpaRepository` + `findBySitePnu(String pnu)`. `TenancyJpaRepository` + `findByUnitId(String unitId)`, `findByUnitIdIn(List<String> unitIds)`. 엔티티는 연관관계 매핑 없이 FK를 평범한 String 컬럼으로만 갖는다(N+1·지연로딩 문제를 원천 차단, 서비스 계층에서 명시적으로 조합).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PersistenceSmokeTest {

    @Autowired SiteJpaRepository siteRepository;
    @Autowired UnitJpaRepository unitRepository;
    @Autowired TenancyJpaRepository tenancyRepository;

    @Test
    void 시드_데이터가_전부_로드된다() {
        assertThat(siteRepository.count()).isEqualTo(6);
        assertThat(unitRepository.count()).isEqualTo(6);
        assertThat(tenancyRepository.count()).isEqualTo(8);
    }

    @Test
    void 자리로_물건을_조회한다() {
        List<UnitEntity> units = unitRepository.findBySitePnu("4113110100100340000");
        assertThat(units).hasSize(1);
        assertThat(units.get(0).getUnitId()).isEqualTo("4113110100100340000-U1");
    }

    @Test
    void 물건으로_이력을_조회하면_두_건이_나온다() {
        List<TenancyEntity> tenancies = tenancyRepository.findByUnitId("4113110100100340000-U1");
        assertThat(tenancies).hasSize(2);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=PersistenceSmokeTest`
Expected: FAIL — 엔티티/리포지토리 클래스가 없어 컴파일 에러.

- [ ] **Step 3: SiteEntity + SiteJpaRepository 작성**

```java
package com.nextstep.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "site")
public class SiteEntity {
    @Id
    private String pnu;
    private String jibunAddress;
    private String roadAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal originalX;
    private BigDecimal originalY;
    private Boolean addressCorrected;
    private String localGovCode;

    protected SiteEntity() {
    }

    public String getPnu() { return pnu; }
    public String getJibunAddress() { return jibunAddress; }
    public String getRoadAddress() { return roadAddress; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getLatitude() { return latitude; }
}
```

```java
package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SiteJpaRepository extends JpaRepository<SiteEntity, String> {

    @Query("SELECT s FROM SiteEntity s WHERE s.jibunAddress LIKE CONCAT('%', :query, '%') "
        + "OR s.roadAddress LIKE CONCAT('%', :query, '%') ORDER BY s.pnu")
    List<SiteEntity> searchByAddress(String query);
}
```

- [ ] **Step 4: UnitEntity + UnitJpaRepository 작성**

```java
package com.nextstep.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unit")
public class UnitEntity {
    @Id
    private String unitId;
    private String sitePnu;
    private String label;
    private String locationSource;

    protected UnitEntity() {
    }

    public String getUnitId() { return unitId; }
    public String getSitePnu() { return sitePnu; }
    public String getLabel() { return label; }
    public String getLocationSource() { return locationSource; }
}
```

```java
package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitJpaRepository extends JpaRepository<UnitEntity, String> {
    List<UnitEntity> findBySitePnu(String sitePnu);
}
```

- [ ] **Step 5: TenancyEntity + TenancyJpaRepository 작성**

```java
package com.nextstep.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "tenancy_record")
public class TenancyEntity {
    @Id
    private Long id;
    private String unitId;
    private String licenseNo;
    private String businessName;
    private String category;
    private String subCategory;
    private LocalDate licensedAt;
    private LocalDate closedAt;
    private String status;
    private String statusDetail;

    protected TenancyEntity() {
    }

    public Long getId() { return id; }
    public String getUnitId() { return unitId; }
    public String getBusinessName() { return businessName; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public LocalDate getLicensedAt() { return licensedAt; }
    public LocalDate getClosedAt() { return closedAt; }
    public String getStatus() { return status; }
}
```

```java
package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TenancyJpaRepository extends JpaRepository<TenancyEntity, Long> {
    List<TenancyEntity> findByUnitId(String unitId);
    List<TenancyEntity> findByUnitIdIn(List<String> unitIds);
}
```

- [ ] **Step 6: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=PersistenceSmokeTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/nextstep/infra/persistence src/test/java/com/nextstep/infra
git commit -m "feat: add JPA entities and repositories"
```

---

