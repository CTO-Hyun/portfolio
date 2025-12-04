package com.example.portfolio.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 오래된 주문을 보관하는 아카이브 엔티티다.
 */
@Entity
@Table(name = "orders_archive")
public class OrderArchive {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false)
    private OffsetDateTime archivedAt;

    @Lob
    private String payload;

    protected OrderArchive() {
    }

    private OrderArchive(Long id, Long userId, OrderStatus status, BigDecimal totalAmount, String idempotencyKey, String payload) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.idempotencyKey = idempotencyKey;
        this.archivedAt = OffsetDateTime.now();
        this.payload = payload;
    }

    /**
     * 기존 주문 정보를 그대로 보존할 수 있도록 JSON 페이로드와 함께 생성한다.
     */
    public static OrderArchive from(Order order, String payload) {
        return new OrderArchive(order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(), order.getIdempotencyKey(), payload);
    }
}
