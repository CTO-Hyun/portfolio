package com.example.portfolio.order.application.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.order.application.OrderArchiveJob;
import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OrderStatus;
import com.example.portfolio.order.infra.OrderArchiveRepository;
import com.example.portfolio.order.infra.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderArchiveJobTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderArchiveRepository orderArchiveRepository;

    private ObjectMapper objectMapper;

    private OrderArchiveJob job;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        job = new OrderArchiveJob(orderRepository, orderArchiveRepository, objectMapper, 30, 50);
    }

    @Test
    void archiveOldOrders_movesBatchesUntilEmpty() {
        Order order = Order.create(1L, "key", "hash");
        ReflectionTestUtils.setField(order, "id", 10L);
        ReflectionTestUtils.setField(order, "createdAt", OffsetDateTime.now().minusDays(40));
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "totalAmount", BigDecimal.TEN);

        when(orderRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(order))
                .thenReturn(Collections.emptyList());

        job.archiveOldOrders();

        verify(orderArchiveRepository).save(any());
        verify(orderRepository).deleteAllInBatch(List.of(order));
        verify(orderRepository, times(2))
                .findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), eq(PageRequest.of(0, 50)));
    }
}
