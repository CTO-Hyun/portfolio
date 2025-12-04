package com.example.portfolio.order.api.dto;

import java.math.BigDecimal;

/**
 * 주문 응답 내 아이템 레코드다.
 */
public record OrderItemResponse(Long productId, int quantity, BigDecimal price, BigDecimal lineAmount) {
}
