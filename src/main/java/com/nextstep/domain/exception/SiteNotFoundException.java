package com.nextstep.domain.exception;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(String pnu) {
        super("해당 자리를 찾을 수 없습니다: " + pnu);
    }
}
