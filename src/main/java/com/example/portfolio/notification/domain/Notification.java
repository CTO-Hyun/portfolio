package com.example.portfolio.notification.domain;

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

/**
 * 주문 생성 알림을 저장하는 엔티티다.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(length = 512)
    private String message;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    private Notification(String eventId, Long orderId, Long userId, NotificationStatus status, String message) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.message = message;
    }

    /**
     * 이벤트 수신 결과를 알림으로 저장한다.
     */
    public static Notification received(String eventId, Long orderId, Long userId, String message) {
        return new Notification(eventId, orderId, userId, NotificationStatus.RECEIVED, message);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
