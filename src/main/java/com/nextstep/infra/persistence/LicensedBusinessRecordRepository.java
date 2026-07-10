package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface LicensedBusinessRecordRepository extends JpaRepository<LicensedBusinessRecordEntity, Long> {

    @Query("SELECT r FROM LicensedBusinessRecordEntity r "
        + "WHERE r.pnu = :query OR r.jibunAddress LIKE CONCAT('%', :query, '%') "
        + "OR r.roadAddress LIKE CONCAT('%', :query, '%') "
        + "ORDER BY r.pnu, r.licensedAt, r.id")
    List<LicensedBusinessRecordEntity> searchByAddress(String query);

    List<LicensedBusinessRecordEntity> findByPnuOrderByLicensedAtAscIdAsc(String pnu);
}
