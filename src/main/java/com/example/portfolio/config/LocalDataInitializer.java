package com.example.portfolio.config;

import com.example.portfolio.inventory.application.CreateProductCommand;
import com.example.portfolio.inventory.application.ProductApplicationService;
import com.example.portfolio.inventory.application.ProductListView;
import com.example.portfolio.user.domain.User;
import com.example.portfolio.user.domain.UserRole;
import com.example.portfolio.user.infra.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로필에서 기본 관리자 계정과 샘플 상품을 자동으로 생성해주는 초기화 컴포넌트다.
 */
@Component
@Profile("local")
@Transactional
public class LocalDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "Admin!234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductApplicationService productApplicationService;

    public LocalDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ProductApplicationService productApplicationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.productApplicationService = productApplicationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdminUser();
        ensureSampleProducts();
    }

    private void ensureAdminUser() {
        String normalizedEmail = ADMIN_EMAIL.toLowerCase();
        userRepository.findByEmail(normalizedEmail).ifPresentOrElse(
                user -> {
                    user.resetCredentials(passwordEncoder.encode(ADMIN_PASSWORD), UserRole.ADMIN);
                    log.info("기존 관리자 계정을 기본 자격 증명으로 초기화했습니다. email={}", normalizedEmail);
                },
                () -> {
                    User admin = User.create(normalizedEmail, passwordEncoder.encode(ADMIN_PASSWORD), "Local Admin", UserRole.ADMIN);
                    userRepository.save(admin);
                    log.info("local 프로필에서 사용할 기본 관리자 계정을 생성했습니다. email={} password={}", normalizedEmail, ADMIN_PASSWORD);
                });
    }

    private void ensureSampleProducts() {
        ProductListView view = productApplicationService.listProducts(0, 1);
        if (view.totalElements() > 0) {
            return;
        }
        List<CreateProductCommand> samples = List.of(
                new CreateProductCommand("SKU-1000", "샘플 티셔츠", "중복 안전성 테스트용 의류", BigDecimal.valueOf(19900), 50),
                new CreateProductCommand("SKU-2000", "샘플 키보드", "Outbox 확인용 주변기기", BigDecimal.valueOf(99000), 30),
                new CreateProductCommand("SKU-3000", "샘플 이어폰", "Notification 테스트용", BigDecimal.valueOf(59000), 100)
        );
        samples.forEach(productApplicationService::createProduct);
        log.info("샘플 상품 {}건을 생성했습니다.", samples.size());
    }
}
