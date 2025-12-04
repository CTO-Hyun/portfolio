package com.example.portfolio.user.infra;

import com.example.portfolio.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 엔티티를 조회/저장하는 Spring Data JPA 저장소다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
