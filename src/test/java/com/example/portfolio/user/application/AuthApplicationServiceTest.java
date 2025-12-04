package com.example.portfolio.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.security.JwtProperties;
import com.example.portfolio.common.security.JwtTokenProvider;
import com.example.portfolio.user.domain.User;
import com.example.portfolio.user.domain.UserRole;
import com.example.portfolio.user.infra.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private PasswordEncoder passwordEncoder;

    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties jwtProperties = new JwtProperties("test-secret-test-secret-test-secret-test", 3600);
        authApplicationService = new AuthApplicationService(userRepository, passwordEncoder, jwtTokenProvider, jwtProperties);
    }

    @Test
    void register_succeeds_whenEmailUnused() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("token");

        AuthResult result = authApplicationService.register(new RegisterCommand("user@example.com", "password123", "Neo"));

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.accessToken()).isEqualTo("token");
    }

    @Test
    void register_fails_whenEmailDuplicate() {
        User existing = User.create("user@example.com", passwordEncoder.encode("password123"), "Neo", UserRole.USER);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authApplicationService.register(new RegisterCommand("user@example.com", "password123", "Neo")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_fails_whenPasswordMismatch() {
        User existing = User.create("user@example.com", passwordEncoder.encode("password123"), "Neo", UserRole.USER);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("user@example.com", "wrong")))
                .isInstanceOf(BusinessException.class);
    }
}
