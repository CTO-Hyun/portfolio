package com.example.portfolio.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import com.example.portfolio.inventory.infra.ProductRepository;
import com.example.portfolio.inventory.infra.StockRepository;
import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.infra.OrderRepository;
import com.example.portfolio.order.infra.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ObjectMapper objectMapper;

    private OrderApplicationService orderApplicationService;

    @BeforeEach
    void setUp() {
        orderApplicationService = new OrderApplicationService(
                orderRepository, outboxEventRepository, productRepository, stockRepository, objectMapper);
    }

    @Test
    void createOrder_returnsExisting_whenIdempotencyKeyFound() {
        Order existing = Order.create(1L, "key", "hash");
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(orderRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(existing));

        OrderView result = orderApplicationService.createOrder(
                new CreateOrderCommand(1L, "key", "hash", List.of(new OrderLineCommand(1L, 1))));

        assertThat(result.id()).isEqualTo(10L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_persistsOrderAndOutbox() throws Exception {
        when(orderRepository.findByIdempotencyKey("key")).thenReturn(Optional.empty());
        Product product = Product.create("SKU", "상품", "설명", BigDecimal.TEN);
        ReflectionTestUtils.setField(product, "id", 1L);
        Stock stock = Stock.initialize(product, 5);
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 200L);
            return event;
        });

        OrderView result = orderApplicationService.createOrder(
                new CreateOrderCommand(1L, "key", "hash", List.of(new OrderLineCommand(1L, 2))));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    void cancelOrder_fails_whenUserMismatch() {
        Order order = Order.create(2L, "key", "hash");
        ReflectionTestUtils.setField(order, "id", 10L);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderApplicationService.cancelOrder(new CancelOrderCommand(1L, 10L)))
                .isInstanceOf(BusinessException.class);
    }
}
