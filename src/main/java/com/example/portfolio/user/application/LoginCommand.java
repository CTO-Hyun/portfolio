package com.example.portfolio.user.application;

/**
 * 로그인 유스케이스에 전달되는 입력 커맨드다.
 */
public record LoginCommand(String email, String password) {
}
