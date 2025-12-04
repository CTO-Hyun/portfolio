package com.example.portfolio.order.infra;

import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.domain.OutboxStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 테이블을 폴링해 Kafka로 이벤트를 발행한다.
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String orderCreatedTopic;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.order-created:order.created}") String orderCreatedTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishReadyEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop100ByStatusInAndAvailableAtBeforeOrderByCreatedAtAsc(
                List.of(OutboxStatus.READY, OutboxStatus.FAILED), OffsetDateTime.now());
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(orderCreatedTopic, event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception ex) {
                log.error("Outbox 이벤트 발행 실패: id={}", event.getId(), ex);
                event.markFailedWithBackoff();
            }
        }
    }
}
