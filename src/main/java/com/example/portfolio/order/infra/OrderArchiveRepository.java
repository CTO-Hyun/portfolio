package com.example.portfolio.order.infra;

import com.example.portfolio.order.domain.OrderArchive;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 아카이브 테이블에 접근하는 저장소다.
 */
public interface OrderArchiveRepository extends JpaRepository<OrderArchive, Long> {
}
