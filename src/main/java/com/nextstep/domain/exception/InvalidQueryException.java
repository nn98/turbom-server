package com.nextstep.domain.exception;

public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException() {
        super("query 파라미터가 필요합니다.");
    }
}
