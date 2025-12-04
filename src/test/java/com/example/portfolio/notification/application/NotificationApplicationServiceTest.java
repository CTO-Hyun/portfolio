package com.example.portfolio.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.notification.domain.Notification;
import com.example.portfolio.notification.infra.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(notificationRepository);
    }

    @Test
    void save_skips_whenAlreadyExists() {
        when(notificationRepository.findByEventId("evt-1")).thenReturn(Optional.of(Notification.received("evt-1", 1L, 1L, "msg")));

        service.saveOrderCreatedNotification("evt-1", 1L, 1L, "msg");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void save_persists_whenNew() {
        when(notificationRepository.findByEventId("evt-1")).thenReturn(Optional.empty());

        service.saveOrderCreatedNotification("evt-1", 1L, 1L, "msg");

        verify(notificationRepository).save(any(Notification.class));
    }
}
