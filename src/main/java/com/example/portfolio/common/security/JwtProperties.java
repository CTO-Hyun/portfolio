package com.example.portfolio.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 관련 설정 값을 타입 세이프하게 바인딩한다.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long accessTokenTtlSeconds) {
}
