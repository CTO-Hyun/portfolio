package com.example.portfolio.order.application;

/**
 * Kafka로 발행되는 주문 생성 이벤트 페이로드다.
 */
public record OrderCreatedEvent(Long eventId, OrderView order) {
}
