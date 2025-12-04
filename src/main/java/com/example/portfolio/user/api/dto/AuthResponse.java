package com.example.portfolio.user.api.dto;

import com.example.portfolio.user.domain.UserRole;

/**
 * 인증 성공 시 발급되는 액세스 토큰 정보를 담는 DTO다.
 */
public record AuthResponse(
        Long userId,
        String email,
        String name,
        UserRole role,
        String accessToken,
        long expiresInSeconds) {
}
