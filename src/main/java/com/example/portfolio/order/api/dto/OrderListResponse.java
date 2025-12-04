package com.example.portfolio.order.api.dto;

import java.util.List;

/**
 * 주문 목록 응답이다.
 */
public record OrderListResponse(List<OrderResponse> orders) {
}
