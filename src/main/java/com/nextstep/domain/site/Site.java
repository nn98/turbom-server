package com.nextstep.domain.site;

import com.nextstep.domain.unit.Unit;
import java.util.List;

public record Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate, List<Unit> units) {
}
