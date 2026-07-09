package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TenancyJpaRepository extends JpaRepository<TenancyEntity, Long> {
    List<TenancyEntity> findByUnitId(String unitId);
    List<TenancyEntity> findByUnitIdIn(List<String> unitIds);
}
