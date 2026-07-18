package com.nextstep.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PersistenceSmokeTest {

    @Autowired LicensedBusinessRecordRepository recordRepository;

    @Test
    void 시드_데이터가_전부_로드된다() {
        assertThat(recordRepository.count()).isEqualTo(1_606_457);
    }

    @Test
    void pnu로_csv_원본행을_조회한다() {
        List<LicensedBusinessRecordEntity> records =
            recordRepository.findByPnuOrderByLicensedAtAscIdAsc("4113110100100340000");
        assertThat(records).hasSize(5);
        assertThat(records).extracting(LicensedBusinessRecordEntity::getBusinessName)
            .contains("동물병원 더 하임", "스웨터메이커스", "그랑핏 아름다운자세");
    }

    @Test
    void 주소로_csv_원본행을_검색한다() {
        List<LicensedBusinessRecordEntity> records = recordRepository.searchByAddress("신흥동");
        assertThat(records).hasSize(8_329);
        assertThat(records).extracting(LicensedBusinessRecordEntity::getPnu)
            .contains("4113110100100340000", "4113110100100300002");
    }

    @Test
    void 같은_pnu의_상세주소가_다른_csv_원본행을_로드한다() {
        List<LicensedBusinessRecordEntity> records =
            recordRepository.findByPnuOrderByLicensedAtAscIdAsc("4113110800105590004");

        assertThat(records).hasSize(81);
        assertThat(recordRepository.findById(102020L).orElseThrow().getSubCategory())
            .isEqualTo("환경전문공사업");
    }
}
