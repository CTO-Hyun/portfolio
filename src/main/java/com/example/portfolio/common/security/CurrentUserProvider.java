package com.example.portfolio.common.security;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import com.example.portfolio.user.domain.User;
import com.example.portfolio.user.infra.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * SecurityContext에서 현재 사용자 식별자를 추출한다.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_ERROR, "인증 정보가 없습니다.");
        }
        String email = extractEmail(authentication);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_ERROR, "사용자를 찾을 수 없습니다."));
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String str) {
            return str;
        }
        throw new BusinessException(ErrorCode.AUTHENTICATION_ERROR, "지원하지 않는 인증 타입입니다.");
    }
}
