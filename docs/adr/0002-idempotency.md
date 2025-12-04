# ADR 0002 – Idempotency Strategy

## Status
Accepted

## Context
- `POST /orders` must honor `Idempotency-Key` so duplicate requests (client retries, network glitches) only produce one order and a single stock decrement.
- Must tolerate concurrent requests and provide deterministic responses.

## Decision
1. Require `Idempotency-Key` header for mutation endpoints (starting with order creation).
2. Persist key + request hash in `orders` table (unique constraint) and store request metadata in Redis to short circuit duplicates before DB load.
3. Wrap application service logic in retryable transactions that check for existing order when the unique constraint fires.

## Consequences
- Redis entry TTL controls dedupe window while DB uniqueness guarantees absolute integrity.
- Clients receive consistent response bodies for duplicate keys.
