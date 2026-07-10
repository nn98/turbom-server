package com.nextstep.application;

import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.unit.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@SpringBootTest
class TenancyQueryServiceTest {

    private static final String DUPLICATE_PNU = "4113110100100990000";
    private static final String DELETE_DUPLICATE_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + DUPLICATE_PNU + "'";
    private static final String INSERT_DUPLICATE_UNIT_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990001, '4113110100100990000', '동물', '동물미용업', 'test-license-1', '중복PNU 1층', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 1, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 99 1층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DUPLICATE_UNIT_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990002, '4113110100100990000', '동물', '동물병원', 'test-license-2', '중복PNU 2층', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 1, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 99 2층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String SAME_JIBUN_PNU = "4113110100100980000";
    private static final String CSV_ADDRESS_UNIT_PNU = "4113110800105590004";
    private static final String DELETE_SAME_JIBUN_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + SAME_JIBUN_PNU + "'";
    private static final String INSERT_SAME_JIBUN_ROAD_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990101, '4113110100100980000', '동물', '동물미용업', 'test-license-3', '동일지번 도로명1', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 2, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_SAME_JIBUN_ROAD_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990102, '4113110100100980000', '동물', '동물병원', 'test-license-4', '동일지번 도로명2', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 2, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";

    @Autowired TenancyQueryService tenancyQueryService;

    @Test
    void 지번주소로_검색하면_일치하는_자리가_나온다() {
        List<Site> results = tenancyQueryService.searchSites("신흥동");
        assertThat(results).extracting(s -> s.pnu().value())
            .contains("4113110100100340000", "4113110100100300002");
        Site site = results.stream()
            .filter(result -> result.pnu().value().equals("4113110100100340000"))
            .findFirst()
            .orElseThrow();
        assertThat(site.coordinate()).isNotNull();
        assertThat(site.coordinate().latitude()).isCloseTo(37.449401980, offset(0.000001));
        assertThat(site.coordinate().longitude()).isCloseTo(127.145534270, offset(0.000001));
    }

    @Test
    void pnu로_자리상세를_조회하면_물건과_통계가_채워진다() {
        Optional<Site> site = tenancyQueryService.findSiteWithUnits("4113110300100280001");
        assertThat(site).isPresent();
        assertThat(site.get().coordinate().latitude()).isCloseTo(37.441549006, offset(0.000001));
        assertThat(site.get().coordinate().longitude()).isCloseTo(127.134741725, offset(0.000001));
        Unit unit = site.get().units().get(0);
        assertThat(unit.statistics().totalTenancyCount()).isEqualTo(1);
        assertThat(unit.statistics().closedCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_pnu는_빈값() {
        assertThat(tenancyQueryService.findSiteWithUnits("9999999999999999999")).isEmpty();
    }

    @Test
    void unitId로_물건상세를_조회하면_csv_이력이_나온다() {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies("4113110100100340000-U1");
        assertThat(unitWithSite).isPresent();
        Unit unit = unitWithSite.get().unit();
        assertThat(unit.tenancies()).hasSize(1);
        assertThat(unitWithSite.get().site().jibunAddress()).contains("신흥동");
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_pnu라도_지번주소가_다르면_물건을_분리한다() {
        Site site = tenancyQueryService.findSiteWithUnits(DUPLICATE_PNU).orElseThrow();

        assertThat(site.units()).hasSize(2);
        assertThat(site.units()).extracting(Unit::unitId)
            .containsExactly(DUPLICATE_PNU + "-U1", DUPLICATE_PNU + "-U2");
        assertThat(site.units()).extracting(Unit::label)
            .containsExactly("1층", "2층");
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void unitId로_조회하면_같은_pnu의_해당_지번주소_이력만_나온다() {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies(DUPLICATE_PNU + "-U2").orElseThrow();

        assertThat(unitWithSite.unit().unitId()).isEqualTo(DUPLICATE_PNU + "-U2");
        assertThat(unitWithSite.unit().tenancies()).hasSize(1);
        assertThat(unitWithSite.unit().tenancies().get(0).businessName()).isEqualTo("중복PNU 2층");
        assertThat(unitWithSite.site().roadAddress()).contains("2층");
        assertThat(tenancyQueryService.findUnitWithTenancies(DUPLICATE_PNU + "-U3")).isEmpty();
    }

    @Test
    @Sql(statements = {DELETE_SAME_JIBUN_PNU, INSERT_SAME_JIBUN_ROAD_1, INSERT_SAME_JIBUN_ROAD_2})
    @Sql(statements = DELETE_SAME_JIBUN_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_pnu에서_도로명주소만_다르면_같은_물건으로_묶는다() {
        Site site = tenancyQueryService.findSiteWithUnits(SAME_JIBUN_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).unitId()).isEqualTo(SAME_JIBUN_PNU + "-U1");
        assertThat(site.units().get(0).tenancies()).hasSize(2);
        assertThat(site.units().get(0).tenancies()).extracting("businessName")
            .containsExactly("동일지번 도로명1", "동일지번 도로명2");
        assertThat(tenancyQueryService.findUnitWithTenancies(SAME_JIBUN_PNU + "-U2")).isEmpty();
    }

    @Test
    void csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다() {
        Site site = tenancyQueryService.findSiteWithUnits(CSV_ADDRESS_UNIT_PNU).orElseThrow();

        assertThat(site.coordinate()).isNotNull();
        assertThat(site.units()).hasSize(28);
        assertThat(site.units().stream().mapToInt(unit -> unit.tenancies().size()).sum()).isEqualTo(59);
        assertThat(site.units()).anySatisfy(unit -> assertThat(unit.tenancies()).hasSize(16));
        assertThat(site.units().stream()
            .flatMap(unit -> unit.tenancies().stream())
            .map(tenancy -> tenancy.status())
            .distinct())
            .containsExactlyInAnyOrder(BusinessStatus.ACTIVE, BusinessStatus.CLOSED);
    }
}
