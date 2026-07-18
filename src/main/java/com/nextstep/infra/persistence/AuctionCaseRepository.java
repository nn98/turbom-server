package com.nextstep.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionCaseRepository extends JpaRepository<AuctionCaseEntity, Long> {
}
