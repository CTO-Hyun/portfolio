package com.example.portfolio.inventory.application;

import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * API와 캐시 계층에서 재사용할 상품 조회 모델이다.
 */
public record ProductView(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        long quantity) implements Serializable {

    public static ProductView from(Product product, Stock stock) {
        long qty = stock != null ? stock.getQuantity() : 0L;
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getDescription(), product.getPrice(), qty);
    }
}
