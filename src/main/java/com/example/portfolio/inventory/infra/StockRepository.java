package com.example.portfolio.inventory.infra;

import com.example.portfolio.inventory.domain.Stock;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 재고 데이터를 조회하고 낙관적 락으로 갱신하는 저장소다.
 */
public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findByProductIdIn(Collection<Long> productIds);
}
