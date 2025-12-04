package com.example.portfolio.order.application;

import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OrderItem;
import com.example.portfolio.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 상세를 API/이벤트 레이어에서 재사용한다.
 */
public record OrderView(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String idempotencyKey,
        OffsetDateTime createdAt,
        List<OrderItemView> items) {

    public static OrderView from(Order order) {
        List<OrderItemView> itemViews = order.getItems().stream()
                .map(item -> new OrderItemView(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getLineAmount()))
                .toList();
        return new OrderView(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getIdempotencyKey(),
                order.getCreatedAt(),
                itemViews);
    }
}
