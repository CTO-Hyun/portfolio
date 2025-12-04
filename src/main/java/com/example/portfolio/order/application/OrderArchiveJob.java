package com.example.portfolio.order.application;

import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OrderArchive;
import com.example.portfolio.order.domain.OrderStatus;
import com.example.portfolio.order.infra.OrderArchiveRepository;
import com.example.portfolio.order.infra.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료/취소 주문을 주기적으로 아카이브 테이블로 옮기는 배치 작업이다.
 */
@Component
public class OrderArchiveJob {

    private static final Logger log = LoggerFactory.getLogger(OrderArchiveJob.class);

    private final OrderRepository orderRepository;
    private final OrderArchiveRepository orderArchiveRepository;
    private final ObjectMapper objectMapper;
    private final int retentionDays;
    private final int chunkSize;

    public OrderArchiveJob(
            OrderRepository orderRepository,
            OrderArchiveRepository orderArchiveRepository,
            ObjectMapper objectMapper,
            @Value("${app.archive.retention-days:30}") int retentionDays,
            @Value("${app.archive.chunk-size:100}") int chunkSize) {
        this.orderRepository = orderRepository;
        this.orderArchiveRepository = orderArchiveRepository;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
        this.chunkSize = chunkSize;
    }

    @Scheduled(cron = "${app.archive.cron:0 0 4 * * *}")
    @Transactional
    public void archiveOldOrders() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(retentionDays);
        int archivedCount = 0;
        while (true) {
            List<Order> batch = orderRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                    List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
                    threshold,
                    PageRequest.of(0, chunkSize));
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(this::archiveOrder);
            orderRepository.deleteAllInBatch(batch);
            archivedCount += batch.size();
        }
        if (archivedCount > 0) {
            log.info("주문 아카이브 완료: count={}, threshold={} ({}일)", archivedCount, threshold, retentionDays);
        }
    }

    private void archiveOrder(Order order) {
        try {
            String payload = objectMapper.writeValueAsString(OrderView.from(order));
            orderArchiveRepository.save(OrderArchive.from(order, payload));
        } catch (Exception ex) {
            log.error("주문 아카이브 직렬화 실패: orderId={}", order.getId(), ex);
            throw new IllegalStateException("주문 아카이브 중 오류 발생", ex);
        }
    }
}
