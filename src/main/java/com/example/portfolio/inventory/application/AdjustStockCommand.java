package com.example.portfolio.inventory.application;

/**
 * 재고 증감 요청을 표현하는 커맨드다.
 */
public record AdjustStockCommand(Long productId, long quantityDelta) {
}
