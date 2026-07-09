package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitJpaRepository extends JpaRepository<UnitEntity, String> {
    List<UnitEntity> findBySitePnu(String sitePnu);
}
