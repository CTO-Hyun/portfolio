package com.example.portfolio.order.api.dto;

import com.example.portfolio.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 상세 응답이다.
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        String idempotencyKey,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items) {
}
