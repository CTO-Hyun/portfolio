package com.example.portfolio.order.domain;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 주문 본문과 하위 아이템을 표현하는 엔티티다.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(length = 128)
    private String requestHash;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    protected Order() {
    }

    private Order(Long userId, String idempotencyKey, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * 주문 생성 시 기본 상태와 키를 설정한다.
     */
    public static Order create(Long userId, String idempotencyKey, String requestHash) {
        return new Order(userId, idempotencyKey, requestHash);
    }

    public void addItem(OrderItem item) {
        item.assignOrder(this);
        items.add(item);
        this.totalAmount = this.totalAmount.add(item.getLineAmount());
    }

    /**
     * CREATED 상태에서만 취소를 허용한다.
     */
    public void cancel() {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "해당 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
