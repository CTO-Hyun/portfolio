package com.example.portfolio.order.application;

import java.util.List;

/**
 * 주문 생성 요청 정보를 캡슐화한다.
 */
public record CreateOrderCommand(Long userId, String idempotencyKey, String requestHash, List<OrderLineCommand> items) {
}
