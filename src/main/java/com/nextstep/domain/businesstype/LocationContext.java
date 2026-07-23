package com.nextstep.domain.businesstype;

import java.time.LocalDate;
import java.util.List;

public record LocationContext(List<SiblingRecord> siblingRecords) {

    public record SiblingRecord(String businessName, LocalDate licensedAt) {
    }

    public boolean hasLaterOtherBusinessName(String businessName, LocalDate licensedAt) {
        return siblingRecords.stream().anyMatch(r ->
            !r.businessName().equals(businessName) && r.licensedAt().isAfter(licensedAt));
    }
}
