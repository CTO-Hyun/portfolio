package com.example.portfolio.order.api;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import com.example.portfolio.common.security.CurrentUser;
import com.example.portfolio.common.security.CurrentUserProvider;
import com.example.portfolio.order.api.dto.CreateOrderRequest;
import com.example.portfolio.order.api.dto.OrderItemRequest;
import com.example.portfolio.order.api.dto.OrderItemResponse;
import com.example.portfolio.order.api.dto.OrderListResponse;
import com.example.portfolio.order.api.dto.OrderResponse;
import com.example.portfolio.order.application.CancelOrderCommand;
import com.example.portfolio.order.application.CreateOrderCommand;
import com.example.portfolio.order.application.OrderApplicationService;
import com.example.portfolio.order.application.OrderLineCommand;
import com.example.portfolio.order.application.OrderView;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 생성/조회/취소 API를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final HexFormat HEX = HexFormat.of();

    private final OrderApplicationService orderApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public OrderController(OrderApplicationService orderApplicationService, CurrentUserProvider currentUserProvider) {
        this.orderApplicationService = orderApplicationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 헤더가 필요합니다.");
        }
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        List<OrderLineCommand> lines = request.items().stream()
                .map(item -> new OrderLineCommand(item.productId(), item.quantity()))
                .toList();
        String requestHash = hashRequest(currentUser.id(), idempotencyKey, request.items());
        OrderView view = orderApplicationService.createOrder(new CreateOrderCommand(currentUser.id(), idempotencyKey, requestHash, lines));
        return ResponseEntity.ok(toResponse(view));
    }

    @GetMapping
    public OrderListResponse listOrders() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        List<OrderResponse> orders = orderApplicationService.listOrders(currentUser.id()).stream()
                .map(this::toResponse)
                .toList();
        return new OrderListResponse(orders);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        OrderView view = orderApplicationService.getOrder(currentUser.id(), orderId);
        return toResponse(view);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        OrderView view = orderApplicationService.cancelOrder(new CancelOrderCommand(currentUser.id(), orderId));
        return toResponse(view);
    }

    private OrderResponse toResponse(OrderView view) {
        List<OrderItemResponse> items = view.items().stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity(), item.price(), item.lineAmount()))
                .toList();
        return new OrderResponse(
                view.id(),
                view.status(),
                view.totalAmount(),
                view.idempotencyKey(),
                view.createdAt(),
                items);
    }

    private String hashRequest(Long userId, String idempotencyKey, List<OrderItemRequest> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(userId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            for (OrderItemRequest item : items) {
                digest.update(Long.toString(item.productId()).getBytes(StandardCharsets.UTF_8));
                digest.update(Integer.toString(item.quantity()).getBytes(StandardCharsets.UTF_8));
            }
            return HEX.formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
