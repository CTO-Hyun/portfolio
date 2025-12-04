package com.example.portfolio.order.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.domain.OutboxStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, "order.created.test");
    }

    @Test
    void publish_marksEventAsPublished() throws Exception {
        OutboxEvent event = OutboxEvent.ready("ORDER", "1", "ORDER_CREATED", "{}");
        when(outboxEventRepository.findTop100ByStatusInAndAvailableAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        publisher.publishReadyEvents();

        verify(kafkaTemplate).send("order.created.test", "1", "{}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }
}
