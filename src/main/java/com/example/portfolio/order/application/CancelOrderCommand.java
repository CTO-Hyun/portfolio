package com.example.portfolio.order.application;

/**
 * 주문 취소 요청 입력 값이다.
 */
public record CancelOrderCommand(Long userId, Long orderId) {
}
