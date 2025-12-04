package com.example.portfolio.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 주문 요청에 포함되는 단일 아이템이다.
 */
public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        int quantity) {
}
