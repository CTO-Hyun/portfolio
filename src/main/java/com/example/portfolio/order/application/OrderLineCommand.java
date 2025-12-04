package com.example.portfolio.order.application;

/**
 * 주문 생성 시 고객이 추가한 단일 아이템 정보를 담는다.
 */
public record OrderLineCommand(Long productId, int quantity) {
}
