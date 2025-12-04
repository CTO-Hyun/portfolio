package com.example.portfolio.common.exception;

/**
 * 도메인 예외를 {@link ErrorCode} 와 매핑하기 위한 기본 예외 클래스다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
