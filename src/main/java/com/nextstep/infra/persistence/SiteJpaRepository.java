package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SiteJpaRepository extends JpaRepository<SiteEntity, String> {

    @Query("SELECT s FROM SiteEntity s WHERE s.jibunAddress LIKE CONCAT('%', :query, '%') "
        + "OR s.roadAddress LIKE CONCAT('%', :query, '%') ORDER BY s.pnu")
    List<SiteEntity> searchByAddress(String query);
}
