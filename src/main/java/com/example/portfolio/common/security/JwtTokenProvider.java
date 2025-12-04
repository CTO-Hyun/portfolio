package com.example.portfolio.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * 상태 없는 인증을 위해 JWT 생성과 검증을 담당한다.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties properties;
    private final UserDetailsService userDetailsService;
    private SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties, UserDetailsService userDetailsService) {
        this.properties = properties;
        this.userDetailsService = userDetailsService;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 사용자 식별자와 권한 목록을 포함하는 서명된 JWT를 생성한다.
     */
    public String generateToken(String subject, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(properties.accessTokenTtlSeconds()));
        Collection<? extends GrantedAuthority> safeAuthorities = authorities == null ? List.of() : authorities;
        List<String> roles = safeAuthorities.stream().map(GrantedAuthority::getAuthority).toList();
        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰에 매핑된 {@link UserDetails} 를 불러와 {@link Authentication} 객체로 변환한다.
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, token, userDetails.getAuthorities());
    }

    /**
     * 서명과 만료 정보를 검사해 컨트롤러 진입 전 토큰을 검증한다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token", ex);
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}
