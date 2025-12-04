package com.example.portfolio.common.security;

import com.example.portfolio.user.domain.UserRole;

/**
 * 인증된 사용자의 정보 스냅샷이다.
 */
public record CurrentUser(Long id, String email, UserRole role) {
}
