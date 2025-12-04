# Architecture Overview

## Context
- Domain: user driven product ordering with strict idempotency and stock invariants.
- Style: hexagonal layered architecture (API → application → domain → infrastructure) enriched with event driven integrations.

## Logical Components
1. **Auth** – Handles registration/login, password hashing, and JWT issuance/verification.
2. **Product/Inventory** – Manages catalog metadata and optimistic locked stock records.
3. **Order** – Coordinates validation, payment placeholder, stock reservations, and outbox event writing.
4. **Outbox Publisher** – Polls `outbox_events`, publishes to Kafka, and marks events as published.
5. **Notification Consumer** – Consumes `ORDER_CREATED` events and persists `notifications` with idempotent guarantees.
6. **Archive Batch** – Moves stale orders to `orders_archive` daily, chunking deletes to reduce lock contention.

## Data Flow
1. Client calls `POST /api/v1/orders` with `Idempotency-Key`.
2. Application layer uses optimistic locking on `stocks.version` and records an outbox event in the same transaction.
3. A scheduled outbox publisher fetches `READY` events, publishes to Kafka, and transitions them to `PUBLISHED`.
4. Kafka consumers persist notifications keyed by `event_id` and update caches if needed.
5. Batch archiver copies completed/cancelled orders older than N days to `orders_archive` (via chunked Spring Batch job) then deletes originals.

## Storage & Messaging
- **MySQL** for transactional consistency.
- **Redis** for product/stock caching and request deduplication metadata.
- **Kafka** for decoupled notifications.

## Observability & Reliability
- Request scoped `requestId` with MDC propagation.
- Structured JSON logs and Prometheus metrics via Actuator.
- Health probes for readiness/liveness (DB + Kafka + Redis).

## Deployment
- Local: Docker Compose for MySQL, Redis, Kafka.
- CI: GitHub Actions running `mvn test` and `mvn package`.
- Delivery: Kubernetes manifests per environment with HPA and secrets for credentials.

## Quality & Testing
- Testcontainers spins up MySQL, Redis, and Kafka, providing the same topology as production in `OrderIntegrationTest`.
- Covered scenarios:
  - Duplicate `Idempotency-Key` requests reuse the original order and keep stock decrements single-shot.
  - Ten concurrent order attempts against five units of stock never push inventory below zero thanks to optimistic locking and retry semantics.
  - Replaying the same Kafka payload only persists one notification row because the consumer enforces `event_id` uniqueness.
