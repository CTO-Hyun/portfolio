package com.example.portfolio.user.application;

/**
 * 회원가입 유스케이스에 필요한 입력값을 담는 커맨드다.
 */
public record RegisterCommand(String email, String password, String name) {
}
