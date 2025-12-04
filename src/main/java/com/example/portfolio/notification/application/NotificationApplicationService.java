package com.example.portfolio.notification.application;

import com.example.portfolio.notification.domain.Notification;
import com.example.portfolio.notification.infra.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka 이벤트를 받아 알림 엔티티로 저장한다.
 */
@Service
public class NotificationApplicationService {

    private final NotificationRepository notificationRepository;

    public NotificationApplicationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void saveOrderCreatedNotification(String eventId, Long orderId, Long userId, String message) {
        if (notificationRepository.findByEventId(eventId).isPresent()) {
            return;
        }
        try {
            notificationRepository.save(Notification.received(eventId, orderId, userId, message));
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE 제약 조건 충돌 시 이미 처리된 이벤트로 간주한다.
        }
    }
}
