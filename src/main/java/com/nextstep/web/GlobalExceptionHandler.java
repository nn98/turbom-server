package com.nextstep.web;

import com.nextstep.domain.exception.InvalidQueryException;
import com.nextstep.domain.exception.SiteNotFoundException;
import com.nextstep.domain.exception.UnitNotFoundException;
import com.nextstep.web.dto.ApiDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidQueryException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_QUERY", e.getMessage()));
    }

    @ExceptionHandler(SiteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSiteNotFound(SiteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("SITE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UnitNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnitNotFound(UnitNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("UNIT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
