# ADR 0003 – Outbox vs Direct Publish

## Status
Accepted

## Context
- Order service must emit `ORDER_CREATED` events whenever a new order is persisted.
- Directly publishing Kafka messages inside the HTTP transaction risks losing events if the process crashes between DB commit and Kafka publish.

## Decision
- Implement the **Transactional Outbox** pattern:
  - `orders` transaction also inserts an `outbox_events` row with payload JSON and status `READY`.
  - A dedicated publisher polls `outbox_events` in batches, publishes to Kafka, and marks rows as `PUBLISHED` (with retry/backoff on failure).
- Publisher runs as a Spring `@Scheduled` component and retries failed rows with exponential backoff while keeping payloads idempotent.
- Kafka payloads carry `eventId` so downstream consumers (notifications) persist with `event_id` UNIQUE constraints for dedupe.

## Consequences
- Guarantees at-least-once delivery between DB and Kafka.
- Introduces extra table + polling job but isolates failure domains.
