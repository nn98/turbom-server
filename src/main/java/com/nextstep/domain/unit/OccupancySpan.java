package com.nextstep.domain.unit;

import java.time.LocalDate;

public record OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull) {

    public boolean overlaps(OccupancySpan other) {
        LocalDate thisEnd = endOrNull == null ? LocalDate.MAX : endOrNull;
        LocalDate otherEnd = other.endOrNull == null ? LocalDate.MAX : other.endOrNull;
        return start.isBefore(otherEnd) && other.start.isBefore(thisEnd);
    }
}
