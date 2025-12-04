package com.example.portfolio.inventory.api.dto;

import java.util.List;

/**
 * 페이지 단위 상품 목록 응답이다.
 */
public record ProductListResponse(
        List<ProductResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size) {
}
