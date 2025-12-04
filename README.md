### 이벤트 기반 주문/재고/알림 백엔드 (Idempotency + Outbox + Kafka + Redis + Batch)

### 한 줄 소개
중복 주문 요청에도 **재고가 정확히 1번만 차감**되도록, **중복 안전성(Idempotency)** 과 **Outbox 패턴**을 적용한 이벤트 기반 백엔드 서비스

---

### 1) 프로젝트 개요
- **도메인**: 주문 / 재고 / 알림
- **핵심 문제**: 동일 주문 요청이 중복으로 들어와도 **주문 생성 1회 + 재고 차감 1회**만 보장
- **핵심 해법**:  
  - API 레벨 중복 안전성(Idempotency-Key)
  - DB 트랜잭션 내 Outbox 적재(커밋과 이벤트 발행의 일관성)
  - 컨슈머 중복 처리 방지(eventId UNIQUE)
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

#### 3.3 주문
- (중복 안전성) 주문 생성 시 **Header: Idempotency-Key** 필수
- 같은 Idempotency-Key로 여러 번 호출되어도:
  - 주문은 **1건만 생성**
  - 재고 차감은 **정확히 1번만 수행**
- 주문 생성 시 **Outbox 이벤트를 함께 적재**

#### 3.4 Outbox 퍼블리셔
- 주문 트랜잭션에서 `outbox_events` INSERT까지 포함(같은 트랜잭션)
- 퍼블리셔가 주기적으로 `outbox_events`를 읽어 Kafka로 발행
- 발행 성공 시 상태를 `PUBLISHED`로 변경
- 장애/재시도 정책: retry count / backoff / DLQ(옵션) 등을 문서화

#### 3.5 알림 컨슈머(중복 처리 방지)
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
  - 이벤트 수신 → notifications 저장(중복 방지)

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

1. Docker 컨테이너 기동  
   - `docker compose up -d`
2. 애플리케이션 실행  
   - `mvn -q spring-boot:run -Dspring-boot.run.profiles=local -P !native,!it`
3. 종료  
   - `Ctrl+C` 로 앱을 내리고 `docker compose down`

#### 9.2 테스트
|구분|설명|명령어|비고|
|---|---|---|---|
|단위 테스트(기본)|JPA/Flyway 등 외부 의존성 없이 빠르게 돌리는 스위트|`mvn -q test -P !native,!it`|Docker 불필요|
|전체 테스트|Testcontainers(MySQL/Kafka/Redis) 포함 통합 시나리오 전부 실행|`mvn -q -Pit test -P !native,!it`|Docker Desktop 필수 (엔진만 켜져 있으면 됨)|
|특정 통합 테스트|문제가 되는 통합 테스트만 개별 실행|`mvn -q -Pit -Dtest=OrderIntegrationTest test -P !native,!it`|역시 Docker 필요|

> 위 명령과 REST 클라이언트(Postman/cURL)만으로 주문 → 아웃박스 → Kafka(알림) → 아카이브 전 과정을 재현할 수 있습니다.

#### 9.3 미니 웹 콘솔 (Thymeleaf + Bootstrap)
로컬 테스트를 빠르게 반복하기 위해 서버 사이드 템플릿(Thymeleaf)로 작성한 단일 페이지 콘솔을 제공합니다.

- URL: [http://localhost:8080/ui](http://localhost:8080/ui)
- 의존성: 별도의 `npm` 빌드 없이 Spring Boot 정적 리소스(`/templates/ui/dashboard.html`, `/static/js/ui.js`)
- 주요 기능
  - 회원가입/로그인 및 JWT 헤더 자동 주입
  - 상품 목록 조회 (게스트 허용), 관리자 모드일 때 상품 생성/재고 증감 폼 활성화
  - 주문 생성(Idempotency-Key 자동 발급) 및 내 주문 조회
  - 최근 API 응답 JSON + Bootstrap Alert로 즉시 피드백 표시
- 기본 관리자 자격 증명 (로컬 프로필에서 자동 초기화)
  - 이메일: `admin@example.com`
  - 비밀번호: `Admin!234`

> 프런트엔드는 단순한 테스트 도구이므로 React/Vue 번들 없이도 바로 사용할 수 있습니다. 커스텀 시나리오를 추가하고 싶다면 `src/main/resources/templates/ui`와 `static/js/ui.js`만 수정하면 됩니다.
- **경로:** `http://localhost:8080/ui`
- **구성:** Thymeleaf 템플릿 1장 + Bootstrap 5 + jQuery. 회원가입/로그인, JWT 보관, 상품 조회/생성, 재고 조정, 주문 작성/중복 안전성 테스트를 한 화면에서 실행 가능.
- **실행 순서**
  1. `docker compose up -d` (MySQL/Redis/Kafka 준비)
  2. `mvn -q spring-boot:run -Dspring-boot.run.profiles=local -P !native,!it`
  3. 브라우저에서 `/ui` 접속 → 회원가입 또는 아래 기본 관리자 계정으로 로그인
     - `admin@example.com / Admin!234` (local 프로필에서 자동 생성)
- **특징:** REST 응답을 JSON으로 즉시 출력하고, Idempotency-Key 재사용 실험/동일 주문 반복 등을 버튼 몇 번으로 재현할 수 있음. Admin 권한이 있어야 상품 생성·재고 조정 카드가 활성화된다.

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
    │   ├── notification/
    │   └── web/                 # Thymeleaf UI 컨트롤러 
    └── main/resources/
        ├── application.yml
        ├── application-local.yml
        ├── templates/ui/        # dashboard.html 등 콘솔 템플릿
        └── static/js/           # ui.js (프론트 스크립트)
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
- 중복 안전성: Idempotency-Key를 DB Unique로 강제하여 중복 주문 생성 방지
- 정합성: 재고는 Optimistic Lock + 재시도로 동시성 환경에서 정확히 차감
- 일관성: Outbox로 “DB 커밋과 이벤트 발행” 간 불일치를 최소화
- 안정성: 컨슈머는 eventId UNIQUE로 중복 소비에도 1회만 처리
- 운영성: 구조화 로그 + 메트릭/헬스체크로 관측 가능성 확보
