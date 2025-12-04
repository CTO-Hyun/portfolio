package com.example.portfolio.notification.infra;

import com.example.portfolio.notification.domain.Notification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 알림 엔티티를 관리하는 저장소다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByEventId(String eventId);
}
