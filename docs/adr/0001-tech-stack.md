# ADR 0001 – Tech Stack

## Status
Accepted

## Context
- Need first-class support for REST, validation, JWT security, Kafka, Redis, and structured logging.
- Require high developer productivity and ecosystem maturity.

## Decision
- Use **Java 18** with **Spring Boot 3.3** and Maven.
- Relational DB: **MySQL 8** (production compatible, optimistic locking, JSON support).
- Cache: **Redis 7** via Spring Data.
- Messaging: **Apache Kafka** for outbox driven integration.
- Observability: Spring Boot Actuator + Micrometer Prometheus registry.

## Consequences
- Access to mature starters and integrations reduces glue code.
- Requires containerized local stack (docker-compose) and GitHub Actions for CI.
