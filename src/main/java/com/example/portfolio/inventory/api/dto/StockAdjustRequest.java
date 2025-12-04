package com.example.portfolio.inventory.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 재고 증감값을 전달하는 요청 DTO다.
 */
public record StockAdjustRequest(
        @NotNull(message = "증감값은 필수입니다.")
        Long quantityDelta) {
}
