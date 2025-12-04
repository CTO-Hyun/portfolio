package com.example.portfolio.inventory.application;

import java.math.BigDecimal;

/**
 * 상품 생성에 필요한 입력을 담는 커맨드다.
 */
public record CreateProductCommand(String sku, String name, String description, BigDecimal price, long initialQuantity) {
}
