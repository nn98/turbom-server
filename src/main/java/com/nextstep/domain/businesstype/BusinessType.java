package com.nextstep.domain.businesstype;

import java.time.LocalDate;

public interface BusinessType {

    LocationCertainty locationCertainty();

    RelatedTypeKeys relatedTypeKeys();

    ReliabilitySignal reliabilitySignal(String businessName, LocalDate licensedAt, LocationContext context);
}
