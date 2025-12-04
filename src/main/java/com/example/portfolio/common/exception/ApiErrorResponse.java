package com.example.portfolio.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 명세된 JSON 오류 응답 포맷을 표현하는 레코드다.
 */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        String path,
        String code,
        String message,
        List<String> details) {
}
