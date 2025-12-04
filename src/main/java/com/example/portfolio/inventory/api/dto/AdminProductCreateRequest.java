package com.example.portfolio.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 관리자 상품 생성 요청 본문을 표현한다.
 */
public record AdminProductCreateRequest(
        @NotBlank(message = "SKU는 필수입니다.")
        @Size(max = 64, message = "SKU는 64자 이하이어야 합니다.")
        String sku,
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 255, message = "상품명은 255자 이하이어야 합니다.")
        String name,
        @Size(max = 2000, message = "설명은 2000자 이하이어야 합니다.")
        String description,
        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = true, message = "가격은 0 이상이어야 합니다.")
        BigDecimal price,
        @Min(value = 0, message = "초기 재고는 0 이상이어야 합니다.")
        long initialQuantity) {
}
