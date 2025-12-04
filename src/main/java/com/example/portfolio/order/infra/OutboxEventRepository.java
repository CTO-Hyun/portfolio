package com.example.portfolio.order.infra;

import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.domain.OutboxStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Outbox 이벤트를 조회/저장하는 저장소다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByStatusInAndAvailableAtBeforeOrderByCreatedAtAsc(List<OutboxStatus> statuses, OffsetDateTime availableBefore);
}
