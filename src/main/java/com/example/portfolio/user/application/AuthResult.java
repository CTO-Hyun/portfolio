package com.example.portfolio.user.application;

import com.example.portfolio.user.domain.UserRole;

/**
 * 인증 성공 후 클라이언트로 반환할 토큰과 사용자 정보를 담는다.
 */
public record AuthResult(
        Long userId,
        String email,
        String name,
        UserRole role,
        String accessToken,
        long expiresInSeconds) {
}
