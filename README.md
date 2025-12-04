### 이벤트 기반 주문/재고/알림 백엔드 (Idempotency + Outbox + Kafka + Redis + Batch)

### 한 줄 소개
중복 주문 요청에도 **재고가 정확히 1번만 차감**되도록, **멱등성(Idempotency)** 과 **Outbox 패턴**을 적용한 이벤트 기반 백엔드 서비스

---

### 1) 프로젝트 개요
- **도메인**: 주문 / 재고 / 알림
- **핵심 문제**: 동일 주문 요청이 중복으로 들어와도 **주문 생성 1회 + 재고 차감 1회**만 보장
- **핵심 해법**:  
  - API 레벨 멱등성(Idempotency-Key)
  - DB 트랜잭션 내 Outbox 적재(커밋과 이벤트 발행의 일관성)
  - 컨슈머 멱등 처리(eventId UNIQUE)
  - 재고 정합성(Optimistic Lock + 재시도)
  - 운영/관측(구조화 로그 + Actuator/Micrometer)

---

### 2) 기술 스택
- Language: **Java 18**
- Framework: **Spring Boot 3.x** (Web, Validation, Security)
- Build: **Maven**
- DB: **MySQL 8** (또는 MariaDB)
- Cache: **Redis**
- Messaging: **Kafka**
- Auth: **JWT**
- DB Migration: **Flyway**
- API 문서: **OpenAPI/Swagger (springdoc-openapi)**
- Observability: **Actuator + Micrometer (Prometheus endpoint)**
- Logging: JSON 구조화 로그
- Test: **JUnit5 + Testcontainers(MySQL/Redis/Kafka)**

---

### 3) 핵심 기능
#### 3.1 인증/인가
- 회원가입/로그인
- JWT 발급/검증
- Role
  - USER: 주문 생성/조회/취소(본인 것만)
  - ADMIN: 상품/재고 관리

#### 3.2 상품/재고
- 상품 생성/조회
- 재고 증감(관리자)
- 재고는 **0 미만 불가**
- 동시성 상황에서도 정합성 유지

#### 3.3 주문(멱등성)
- 주문 생성 시 **Header: Idempotency-Key 필수**
- 같은 Idempotency-Key로 여러 번 호출되어도:
  - 주문은 **1건만 생성**
  - 재고 차감은 **정확히 1번만 수행**
- 주문 생성 시 **Outbox 이벤트를 함께 적재**

#### 3.4 Outbox 퍼블리셔
- 주문 트랜잭션에서 `outbox_events` INSERT까지 포함(같은 트랜잭션)
- 퍼블리셔가 주기적으로 `outbox_events`를 읽어 Kafka로 발행
- 발행 성공 시 상태를 `PUBLISHED`로 변경
- 장애/재시도 정책: retry count / backoff / DLQ(옵션) 등을 문서화

#### 3.5 알림 컨슈머(멱등)
- Kafka `ORDER_CREATED` 이벤트 소비
- `notifications` 테이블 저장
- `event_id UNIQUE`로 **중복 이벤트 처리 방지**

#### 3.6 배치(아카이브/삭제)
- 매일 1회:
  - 완료/취소 주문 중 N일 지난 데이터를 `orders_archive`로 이동
  - live 테이블에서 청크 방식으로 삭제
- 처리 결과 요약 로그 기록

---

### 4) 아키텍처 요약
#### 4.1 이벤트 흐름(주문 생성)
1) Client → `POST /orders` (Idempotency-Key)
2) Order 트랜잭션
   - Idempotency-Key 중복 검사/저장
   - 재고 차감(Optimistic Lock + 재시도)
   - 주문/아이템 저장
   - Outbox 이벤트 저장
3) Outbox Publisher
   - Outbox 조회 → Kafka 발행 → 상태 업데이트
4) Notification Consumer
   - 이벤트 수신 → notifications 저장(멱등)

#### 4.2 간단 플로우(ASCII)
```text
Client
  │  POST /orders (Idempotency-Key)
  ▼
Order API (Transaction)
  ├─ orders(idempotency_key UNIQUE) upsert/check
  ├─ stocks(version) optimistic lock + retry
  ├─ save order + items
  └─ save outbox_events (NEW)
  ▼ commit
Outbox Publisher (poll)
  ├─ fetch NEW outbox
  ├─ publish to Kafka
  └─ mark PUBLISHED (or retry)
  ▼
Kafka Topic: ORDER_CREATED
  ▼
Notification Consumer
  ├─ insert notifications(event_id UNIQUE)
  └─ idempotent processing
```

---

### 5) 데이터 모델(Flyway)
필수 테이블:
- `users` (email UNIQUE)
- `products` (sku UNIQUE)
- `stocks` (product_id PK, quantity, version)
- `orders` (idempotency_key UNIQUE)
- `order_items`
- `outbox_events` (status, created_at 인덱스)
- `notifications` (event_id UNIQUE)
- `orders_archive`

동시성 전략:
- `stocks.version` 기반 **Optimistic Lock**
- 충돌 시 **제한 횟수 재시도**(예: 3~5회)

---

### 6) API 설계
Base: `/api/v1`

Auth:
- `POST /auth/register`
- `POST /auth/login`

Products:
- `GET /products`
- `GET /products/{productId}`
- `POST /admin/products`
- `POST /admin/products/{productId}/stock-adjust`

Orders:
- `POST /orders` (Header: `Idempotency-Key` 필수)
- `GET /orders`
- `GET /orders/{orderId}`
- `POST /orders/{orderId}/cancel`

표준 에러 응답:
```json
{
  "timestamp": "...",
  "path": "...",
  "code": "VALIDATION_ERROR",
  "message": "사람이 읽을 수 있는 메시지",
  "details": []
}
```

---

### 7) Redis 사용
- 상품 캐시:
  - `product:{id}`
  - `product:list:{page}:{size}`
- 상품/재고 변경 시 캐시 무효화 전략 포함(예: 해당 키 삭제)

---

### 8) 운영/관측(Observability)
- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`
- 요청 단위 식별자(requestId) 생성/전파
- 구조화 로그에 `requestId`, `userId`, `orderId`(해당 시) 포함

---

### 9) 로컬 실행
#### 9.1 Docker Compose
- 포함 서비스: mysql, redis, kafka

`docker compose up -d`

`mvn spring-boot:run -Dspring-boot.run.profiles=local`

#### 9.2 테스트
- 단위 테스트(기본):`mvn -q test`

- 통합 테스트 포함(도커 필요):`mvn -q -Pit test`

- 통합 테스트 단일 클래스 실행:`mvn -q -Pit -Dtest=OrderIntegrationTest test`

---

### 10) 통합 테스트 시나리오(Testcontainers)
반드시 검증:
- 같은 Idempotency-Key 재호출 시 주문 1건만 생성
- 동시 주문에서도 재고 음수 불가
- 같은 이벤트 2번 소비해도 notifications 1건만 생성

---

### 11) CI(GitHub Actions)
- PR / main push 시:
  - JDK 18 세팅
  - `mvn test`
  - `mvn package`

---

### 12) K8s 배포 형태 제시
- Namespace, ConfigMap, Secret, Deployment, Service, HPA, Ingress
- readiness/liveness: actuator health 기반
- 리소스 requests/limits 포함

---

### 13) 레포지토리 구조
```text
/
├── README.md
├── pom.xml
├── docker-compose.yml
├── .github/workflows/ci.yml
├── docs/
│   ├── architecture.md
│   ├── api-examples.md
│   └── adr/
│       ├── 0001-tech-stack.md
│       ├── 0002-idempotency.md
│       └── 0003-outbox-vs-direct-publish.md
├── k8s/base/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── hpa.yaml
│   └── ingress.yaml
└── src/
    ├── main/java/com/example/portfolio/
    │   ├── common/
    │   ├── user/
    │   ├── inventory/
    │   ├── order/
    │   └── notification/
    └── main/resources/
        ├── application.yml
        ├── application-local.yml
        ├── application-test.yml
        └── db/migration/
```

레이어 규칙:
- api: Controller, Request/Response DTO
- application: 유스케이스/서비스(트랜잭션 경계)
- domain: 엔티티/도메인 규칙
- infra: Repository, Kafka, Redis 등 외부 의존 영역

---

### 14) 설계 포인트(요약)
- 멱등성: Idempotency-Key를 DB Unique로 강제하여 중복 주문 생성 방지
- 정합성: 재고는 Optimistic Lock + 재시도로 동시성 환경에서 정확히 차감
- 일관성: Outbox로 “DB 커밋과 이벤트 발행” 간 불일치를 최소화
- 안정성: 컨슈머는 eventId UNIQUE로 중복 소비에도 1회만 처리
- 운영성: 구조화 로그 + 메트릭/헬스체크로 관측 가능성 확보

