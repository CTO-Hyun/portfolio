package com.example.portfolio.inventory.domain;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 상품별 수량과 낙관적 락 버전을 추적하는 재고 엔티티다.
 */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private long quantity;

    @Version
    private long version;

    protected Stock() {
    }

    private Stock(Product product, long quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    /**
     * 신규 상품에 대한 초기 재고를 생성한다.
     */
    public static Stock initialize(Product product, long initialQuantity) {
        if (initialQuantity < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "초기 재고는 음수가 될 수 없습니다.");
        }
        return new Stock(product, initialQuantity);
    }

    /**
     * 증감 요청을 적용한 뒤 음수 여부를 검사한다.
     */
    public void applyDelta(long delta) {
        long next = this.quantity + delta;
        if (next < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "재고가 음수가 될 수 없습니다.");
        }
        this.quantity = next;
    }

    public Long getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public long getQuantity() {
        return quantity;
    }
}
