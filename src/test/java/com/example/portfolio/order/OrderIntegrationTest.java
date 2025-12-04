package com.example.portfolio.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import com.example.portfolio.inventory.infra.ProductRepository;
import com.example.portfolio.inventory.infra.StockRepository;
import com.example.portfolio.notification.infra.NotificationRepository;
import com.example.portfolio.order.application.CreateOrderCommand;
import com.example.portfolio.order.application.OrderApplicationService;
import com.example.portfolio.order.application.OrderLineCommand;
import com.example.portfolio.order.application.OrderView;
import com.example.portfolio.order.domain.OutboxEvent;
import com.example.portfolio.order.infra.OutboxEventRepository;
import com.example.portfolio.order.infra.OrderArchiveRepository;
import com.example.portfolio.order.infra.OrderRepository;
import com.example.portfolio.order.infra.OutboxPublisher;
import com.example.portfolio.user.domain.User;
import com.example.portfolio.user.domain.UserRole;
import com.example.portfolio.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Tag("integration")
class OrderIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.39");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.5"))
            .withExposedPorts(6379);

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderArchiveRepository orderArchiveRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.order-created:order.created}")
    private String orderCreatedTopic;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        orderArchiveRepository.deleteAll();
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void sameIdempotencyKeyCreatesSingleOrderAndNotifiesOnce() {
        User savedUser = userRepository.save(User.create("integration@example.com", passwordEncoder.encode("password"), "Tester", UserRole.USER));
        Product savedProduct = productRepository.save(Product.create("SKU-IT", "인증테스트상품", "설명", BigDecimal.valueOf(10)));
        stockRepository.save(Stock.initialize(savedProduct, 5));

        String idemKey = UUID.randomUUID().toString();
        List<OrderLineCommand> lines = List.of(new OrderLineCommand(savedProduct.getId(), 2));
        CreateOrderCommand command = new CreateOrderCommand(savedUser.getId(), idemKey, "hash", lines);

        OrderView first = orderApplicationService.createOrder(command);
        OrderView duplicate = orderApplicationService.createOrder(command);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(stockRepository.findById(savedProduct.getId()).orElseThrow().getQuantity()).isEqualTo(3);

        outboxPublisher.publishReadyEvents();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void concurrentOrdersNeverOverdrawStock() throws Exception {
        User savedUser = userRepository.save(User.create("race@example.com", passwordEncoder.encode("password"), "Concurrent", UserRole.USER));
        Product savedProduct = productRepository.save(Product.create("SKU-RACE", "동시성상품", "설명", BigDecimal.valueOf(7)));
        int initialQuantity = 5;
        stockRepository.save(Stock.initialize(savedProduct, initialQuantity));

        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderApplicationService.createOrder(new CreateOrderCommand(
                            savedUser.getId(),
                            UUID.randomUUID().toString(),
                            "hash",
                            List.of(new OrderLineCommand(savedProduct.getId(), 1))));
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception ex) {
                    return false;
                }
            }));
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long successCount = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(Boolean::booleanValue)
                .count();

        long remainingQuantity = stockRepository.findById(savedProduct.getId()).orElseThrow().getQuantity();

        assertThat(successCount).isLessThanOrEqualTo(initialQuantity);
        assertThat(remainingQuantity).isGreaterThanOrEqualTo(0);
        assertThat(successCount + remainingQuantity).isEqualTo(initialQuantity);
        assertThat(orderRepository.count()).isEqualTo(successCount);
    }

    @Test
    void duplicateEventConsumptionRemainsIdempotent() throws Exception {
        User savedUser = userRepository.save(User.create("event@example.com", passwordEncoder.encode("password"), "Notifier", UserRole.USER));
        Product savedProduct = productRepository.save(Product.create("SKU-EVT", "이벤트상품", "설명", BigDecimal.valueOf(12)));
        stockRepository.save(Stock.initialize(savedProduct, 1));

        CreateOrderCommand command = new CreateOrderCommand(
                savedUser.getId(),
                UUID.randomUUID().toString(),
                "hash",
                List.of(new OrderLineCommand(savedProduct.getId(), 1)));

        orderApplicationService.createOrder(command);
        outboxPublisher.publishReadyEvents();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.count()).isEqualTo(1);
        });

        OutboxEvent event = outboxEventRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
        kafkaTemplate.send(orderCreatedTopic, event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.count()).isEqualTo(1);
        });
    }
}
