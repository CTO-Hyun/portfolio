package com.example.portfolio.user.api;

import com.example.portfolio.user.api.dto.AuthResponse;
import com.example.portfolio.user.api.dto.LoginRequest;
import com.example.portfolio.user.api.dto.RegisterRequest;
import com.example.portfolio.user.application.AuthApplicationService;
import com.example.portfolio.user.application.AuthResult;
import com.example.portfolio.user.application.LoginCommand;
import com.example.portfolio.user.application.RegisterCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입/로그인 REST 엔드포인트를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        var result = authApplicationService.register(new RegisterCommand(request.email(), request.password(), request.name()));
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authApplicationService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(toResponse(result));
    }

    private AuthResponse toResponse(AuthResult result) {
        return new AuthResponse(
                result.userId(),
                result.email(),
                result.name(),
                result.role(),
                result.accessToken(),
                result.expiresInSeconds());
    }
}
