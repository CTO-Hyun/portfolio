package com.example.portfolio.inventory.api.dto;

import java.math.BigDecimal;

/**
 * 단일 상품 응답을 나타낸다.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        long quantity) {
}
