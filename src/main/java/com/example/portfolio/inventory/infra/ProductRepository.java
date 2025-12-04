package com.example.portfolio.inventory.infra;

import com.example.portfolio.inventory.domain.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 엔티티 CRUD를 담당하는 저장소다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
}
