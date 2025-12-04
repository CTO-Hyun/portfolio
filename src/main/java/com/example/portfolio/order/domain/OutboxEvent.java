package com.example.portfolio.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 주문 생성 시 Kafka 발행을 보장하기 위한 Outbox 엔티티다.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retries;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime availableAt;

    @Column
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.READY;
        this.availableAt = OffsetDateTime.now();
    }

    /**
     * 지정된 페이로드로 Outbox 이벤트를 생성한다.
     */
    public static OutboxEvent ready(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    public void updatePayload(String payload) {
        this.payload = payload;
    }

    /**
     * 발행 성공 시 상태와 발행 시각을 기록한다.
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
    }

    /**
     * 실패 시 재시도 횟수를 늘리고 다음 가용 시각을 갱신한다.
     */
    public void markFailedWithBackoff() {
        this.status = OutboxStatus.FAILED;
        this.retries += 1;
        this.availableAt = OffsetDateTime.now().plusSeconds(Math.min(60, retries * 5L));
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetries() {
        return retries;
    }

    public OffsetDateTime getAvailableAt() {
        return availableAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }
}
