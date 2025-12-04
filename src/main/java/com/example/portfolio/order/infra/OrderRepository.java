package com.example.portfolio.order.infra;

import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주문 엔티티를 관리하는 저장소다.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List<OrderStatus> statuses, OffsetDateTime createdBefore, Pageable pageable);
}
