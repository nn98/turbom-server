package com.nextstep.domain.exception;

public class UnitNotFoundException extends RuntimeException {
    public UnitNotFoundException(String unitId) {
        super("해당 물건을 찾을 수 없습니다: " + unitId);
    }
}
