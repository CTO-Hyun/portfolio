package com.example.portfolio.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 주문 생성 요청 본문이다.
 */
public record CreateOrderRequest(@NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") List<@Valid OrderItemRequest> items) {
}
