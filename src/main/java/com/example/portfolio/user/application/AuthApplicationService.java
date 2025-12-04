package com.example.portfolio.user.application;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import com.example.portfolio.common.security.JwtTokenProvider;
import com.example.portfolio.common.security.JwtProperties;
import com.example.portfolio.user.domain.User;
import com.example.portfolio.user.domain.UserRole;
import com.example.portfolio.user.infra.UserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입과 로그인 유스케이스를 담당하는 애플리케이션 서비스다.
 */
@Service
@Transactional(readOnly = true)
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthApplicationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 이메일 중복을 검사한 뒤 사용자를 저장하고 액세스 토큰을 발급한다.
     */
    @Transactional
    public AuthResult register(RegisterCommand command) {
        String normalizedEmail = command.email().toLowerCase();
        userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT_ERROR, "이미 사용 중인 이메일입니다.");
        });
        User user = User.create(
                normalizedEmail,
                passwordEncoder.encode(command.password()),
                command.name(),
                UserRole.USER);
        User saved = userRepository.save(user);
        return issueToken(saved);
    }

    /**
     * 이메일과 비밀번호를 검증하고 성공 시 토큰을 재발급한다.
     */
    public AuthResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email().toLowerCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_ERROR, "잘못된 이메일 또는 비밀번호입니다."));
        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_ERROR, "잘못된 이메일 또는 비밀번호입니다.");
        }
        return issueToken(user);
    }

    /**
     * 사용자 역할을 권한으로 매핑한 뒤 JWT를 생성해 응답 모델로 변환한다.
     */
    private AuthResult issueToken(User user) {
        String token = jwtTokenProvider.generateToken(
                user.getEmail(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        return new AuthResult(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                token,
                jwtProperties.accessTokenTtlSeconds());
    }
}
