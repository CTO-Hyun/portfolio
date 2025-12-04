package com.example.portfolio.notification.consumer;

import com.example.portfolio.notification.application.NotificationApplicationService;
import com.example.portfolio.order.application.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ORDER_CREATED 이벤트를 구독해 notifications 테이블에 저장한다.
 */
@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final NotificationApplicationService notificationApplicationService;

    public OrderCreatedConsumer(NotificationApplicationService notificationApplicationService) {
        this.notificationApplicationService = notificationApplicationService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created:order.created}", groupId = "notification-consumer")
    public void consume(OrderCreatedEvent message) {
        log.info("주문 이벤트 수신: eventId={}, orderId={}", message.eventId(), message.order().id());
        notificationApplicationService.saveOrderCreatedNotification(
                String.valueOf(message.eventId()),
                message.order().id(),
                message.order().userId(),
                "주문이 생성되었습니다.");
    }
}
