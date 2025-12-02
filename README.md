## 0) 한 줄 소개
- 중복 주문 요청에도 재고가 정확히 1번만 차감되도록 멱등성과 Outbox 패턴을 적용한 이벤트 기반 백엔드 서비스

## 1) 프로젝트 목표
- Java + Spring Boot 기반 REST API 구현
- RDB 트랜잭션/락을 통한 정합성 보장
- Redis 캐시 적용(필수)
- Kafka 이벤트 발행/소비 구현
- Outbox 패턴으로 “DB 커밋 + 이벤트 발행”의 일관성 확보
- 배치로 아카이브/삭제 처리(청크 방식)
- Docker Compose 로컬 실행 재현성 확보
- CI에서 테스트/빌드 자동화
- K8s 매니페스트로 배포 형태 제시

## 2) 기술 스택(최종)
- Language: Java 21
- Build: Maven
- Framework: Spring Boot 3.x (Web, Validation, Security)
- DB: MySQL 8 (또는 MariaDB)
- Cache: Redis
- Messaging: Kafka
- Auth: JWT
- API 문서: OpenAPI/Swagger (springdoc-openapi)
- Observability: Actuator + Micrometer (Prometheus endpoint)
- Logging: JSON 구조화 로그
- Test: JUnit5 + Testcontainers(MySQL/Redis/Kafka)

## 3) 레포지토리 구조
/
  README.md
  pom.xml
  docker-compose.yml
  .github/workflows/ci.yml
  docs/
    architecture.md
    adr/
      0001-tech-stack.md
      0002-idempotency.md
      0003-outbox-vs-direct-publish.md
    api-examples.md
  k8s/
    base/
      namespace.yaml
      configmap.yaml
      secret.yaml
      deployment.yaml
      service.yaml
      hpa.yaml
      ingress.yaml
  src/main/java/com/example/portfolio/
    PortfolioApplication.java
    common/
      config/
      exception/
      logging/
      security/
      util/
    order/
      api/
      application/
      domain/
      infra/
    inventory/
      api/
      application/
      domain/
      infra/
    notification/
      consumer/
      application/
      domain/
      infra/
  src/main/resources/
    application.yml
    application-local.yml
    application-test.yml
    db/migration/
  src/test/java/...

레이어 규칙:
- api: Controller, Request/Response DTO
- application: 유스케이스/서비스(트랜잭션 경계)
- domain: 엔티티/도메인 규칙
- infra: Repository, Kafka, Redis 등 외부 의존 영역

## 4) 핵심 기능 요구사항
### 4.1 인증/인가
- 회원가입/로그인
- JWT 발급/검증
- 권한:
  - USER: 주문 생성/조회/취소(본인 것만)
  - ADMIN: 상품/재고 관리

### 4.2 상품/재고
- 상품 생성/조회
- 재고 증감
규칙:
- 재고는 0 미만으로 내려가면 안 된다
- 동시성 상황에서도 정합성을 유지해야 한다

### 4.3 주문
- 주문 생성(요청 헤더 Idempotency-Key 필수)
- 내 주문 목록/상세
- 주문 취소(상태 CREATED일 때만)
규칙:
- 같은 Idempotency-Key로 여러 번 호출되어도 주문은 1번만 생성되어야 한다
- 재고 차감은 정확히 1번만 수행되어야 한다
- 주문 생성 이벤트를 Outbox에 적재한다

### 4.4 Outbox 퍼블리셔(필수)
- 주문 생성 트랜잭션에서 outbox_events를 함께 저장한다
- 별도 퍼블리셔가 outbox_events를 폴링하여 Kafka로 발행한다
- 발행 성공 시 outbox_events 상태를 PUBLISHED로 변경한다
- 장애/재시도 정책을 ADR에 남긴다

### 4.5 알림 컨슈머(필수)
- Kafka에서 ORDER_CREATED 이벤트를 소비한다
- notifications 테이블에 저장한다
- eventId UNIQUE로 중복 처리를 막는다(컨슈머 멱등성)

### 4.6 배치/스케줄러(필수)
- 매일 1회:
  - 완료/취소 주문 중 N일 지난 데이터를 archive로 이동
  - live 테이블에서 청크 방식으로 삭제
- 처리 결과 요약 로그를 남긴다

## 5) 데이터 모델(Flyway)
필수 테이블:
- users (email UNIQUE)
- products (sku UNIQUE)
- stocks (product_id PK, quantity, version)
- orders (idempotency_key UNIQUE)
- order_items
- outbox_events (status, created_at 인덱스)
- notifications (event_id UNIQUE)
- orders_archive

동시성:
- stocks.version 기반 Optimistic Lock 적용
- OptimisticLockException 발생 시 제한 횟수 재시도

## 6) API 설계
Base: /api/v1

Auth:
- POST /auth/register
- POST /auth/login

Products:
- GET /products
- GET /products/{productId}
- POST /admin/products
- POST /admin/products/{productId}/stock-adjust

Orders:
- POST /orders (Header: Idempotency-Key 필수)
- GET /orders
- GET /orders/{orderId}
- POST /orders/{orderId}/cancel

표준 에러 응답:
{
  "timestamp": "...",
  "path": "...",
  "code": "VALIDATION_ERROR",
  "message": "사람이 읽을 수 있는 메시지",
  "details": [...]
}

## 7) Redis 사용(최소 요구)
- 상품 캐시:
  - product:{id}
  - product:list:{page}:{size}
- 상품/재고 변경 시 캐시 무효화 전략 포함

## 8) 관측 가능성
- /actuator/health
- /actuator/metrics
- /actuator/prometheus
- requestId를 필터에서 생성/전파한다
- 구조화 로그에 requestId, userId, orderId(해당 시) 포함

## 9) 로컬 실행(docker-compose)
docker-compose 포함:
- mysql
- redis
- kafka

실행:
- docker compose up -d
- mvn spring-boot:run -Dspring-boot.run.profiles=local

테스트:
- mvn test

## 10) 테스트 요구사항(Testcontainers)
- MySQL/Redis/Kafka 통합 테스트를 포함한다
- 반드시 검증할 시나리오:
  - 같은 Idempotency-Key 재호출 시 주문 1건만 생성
  - 동시 주문에서도 재고 음수 불가
  - 같은 이벤트 2번 소비해도 notifications 1건만 생성

## 11) CI(GitHub Actions)
- PR 및 main push 시:
  - JDK 21 세팅
  - mvn test
  - mvn package

## 12) K8s 매니페스트
- Namespace, ConfigMap, Secret, Deployment, Service, HPA
- readiness/liveness는 actuator health 기반
- 리소스 requests/limits 포함

## 13) 코딩 규칙
- 모든 비즈니스 로직에 주석을 작성한다
- 주석에는 이모지/아이콘을 절대 넣지 않는다
- Controller는 얇게, 트랜잭션은 application 계층에서 관리한다
- @ControllerAdvice로 예외를 중앙 처리한다
- 엔티티를 API 응답으로 직접 노출하지 않는다

## 14) 구현 순서(Codex 실행 순서)
1) Maven 기반 Spring Boot 초기화 + 의존성 + profile + docker-compose
2) 인증/인가(JWT)
3) 상품/재고 + optimistic lock
4) 주문 + Idempotency-Key
5) Outbox 저장 + 퍼블리셔 + Kafka 발행
6) 컨슈머 + notifications 멱등성
7) 배치 아카이브/삭제
8) Testcontainers 통합 테스트 + 문서(ADR/아키텍처) + CI + K8s
