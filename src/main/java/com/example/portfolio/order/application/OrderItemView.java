package com.example.portfolio.order.application;

import java.math.BigDecimal;

/**
 * 응답/이벤트에서 사용하는 주문 아이템 표현이다.
 */
public record OrderItemView(Long productId, int quantity, BigDecimal price, BigDecimal lineAmount) {
}
