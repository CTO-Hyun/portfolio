package com.example.portfolio.order.application;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import com.example.portfolio.inventory.infra.ProductRepository;
import com.example.portfolio.inventory.infra.StockRepository;
import com.example.portfolio.order.domain.Order;
import com.example.portfolio.order.domain.OrderItem;
import com.example.portfolio.order.domain.OrderStatus;
import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.infra.OrderRepository;
import com.example.portfolio.order.infra.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성/조회/취소 및 Outbox 적재를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ProductRepository productRepository,
            StockRepository stockRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 멱등키 중복 시 기존 주문을 반환하고, 신규일 경우 재고 차감과 outbox 적재를 수행한다.
     */
    @Transactional
    public OrderView createOrder(CreateOrderCommand command) {
        Optional<Order> existing = orderRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return OrderView.from(existing.get());
        }
        try {
            Order order = Order.create(command.userId(), command.idempotencyKey(), command.requestHash());
            Map<Long, Product> productMap = loadProducts(command.items());
            for (OrderLineCommand itemCommand : command.items()) {
                Product product = productMap.get(itemCommand.productId());
                if (product == null) {
                    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다.");
                }
                Stock stock = stockRepository.findById(product.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "재고를 찾을 수 없습니다."));
                stock.applyDelta(-itemCommand.quantity());
                OrderItem item = OrderItem.of(product.getId(), itemCommand.quantity(), product.getPrice());
                order.addItem(item);
            }
            Order saved = orderRepository.save(order);
            persistOutbox(saved);
            return OrderView.from(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Idempotency key collision detected", ex);
            Order fallback = orderRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "주문 처리 중 오류가 발생했습니다."));
            return OrderView.from(fallback);
        }
    }

    /**
     * 로그인 사용자가 본인 주문을 조회할 수 있도록 한다.
     */
    public OrderView getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "주문을 찾을 수 없습니다."));
        ensureOwner(order, userId);
        return OrderView.from(order);
    }

    public List<OrderView> listOrders(Long userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderView::from)
                .toList();
    }

    /**
     * CREATED 상태의 주문만 취소하며 재시도 가능하도록 한다.
     */
    @Transactional
    public OrderView cancelOrder(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "주문을 찾을 수 없습니다."));
        ensureOwner(order, command.userId());
        order.cancel();
        return OrderView.from(order);
    }

    private Map<Long, Product> loadProducts(List<OrderLineCommand> items) {
        List<Long> productIds = items.stream().map(OrderLineCommand::productId).distinct().toList();
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private void persistOutbox(Order order) {
        try {
            OutboxEvent event = OutboxEvent.ready("ORDER", order.getId().toString(), "ORDER_CREATED", "{}");
            OutboxEvent saved = outboxEventRepository.save(event);
            String payload = objectMapper.writeValueAsString(new OrderCreatedEvent(saved.getId(), OrderView.from(order)));
            saved.updatePayload(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }

    private void ensureOwner(Order order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTHORIZATION_ERROR, "본인 주문만 접근할 수 있습니다.");
        }
    }
}
