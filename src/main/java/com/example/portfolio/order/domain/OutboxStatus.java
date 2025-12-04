package com.example.portfolio.order.domain;

/**
 * Outbox 이벤트 처리 상태다.
 */
public enum OutboxStatus {
    READY,
    PUBLISHED,
    FAILED
}
