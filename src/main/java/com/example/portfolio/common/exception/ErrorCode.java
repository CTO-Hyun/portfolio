package com.example.portfolio.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 오류 응답 규격에서 사용될 표준 에러 코드를 정의한다.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    AUTHENTICATION_ERROR(HttpStatus.UNAUTHORIZED),
    AUTHORIZATION_ERROR(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT_ERROR(HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
