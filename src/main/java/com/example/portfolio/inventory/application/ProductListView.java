package com.example.portfolio.inventory.application;

import java.io.Serializable;
import java.util.List;

/**
 * 페이지 단위 상품 목록을 나타내며 캐시에 그대로 저장될 수 있다.
 */
public record ProductListView(
        List<ProductView> items,
        long totalElements,
        int totalPages,
        int page,
        int size) implements Serializable {
}
