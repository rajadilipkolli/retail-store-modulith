ADR-0003: Define Order Processing and Compensation
Status: Accepted
Date: 2026-10-22
Decision owners: Repository maintainers
Related specifications: spec/03-inventory-module.md, spec/04-payments-module.md, spec/05-orders-orchestration.md
Supersedes: None
Superseded by: None
1. Context

Order processing involves multiple business operations across bounded contexts, including product validation, inventory reservation, payment reservation, and order persistence.

These operations commit across independent database schemas (`orders`, `inventory`, `payment`). A failure in a later operation cannot automatically undo earlier database commits or external side effects without coordinated compensation.

2. Decision

The Orders module owns order lifecycle management and orchestrates the order creation workflow synchronously through the public APIs of Catalog, Inventory, and Payments using a multi-step orchestrated saga:

1. Request Validation: Validate payload integrity, verify customer exists via `PaymentsFacade.findCustomerById()`, and verify catalog product existence and unit prices via `CatalogFacade`.
2. Pre-commit and Idempotency Claim: Atomically persist an order in the `orders` schema with status `NEW` and claim the `(customerId, idempotencyKey)` lock in `orders.order_idempotency` with a versioned canonical request fingerprint.
3. Inventory Reservation: Request stock reservation via `InventoryFacade.reserve()`. If insufficient stock or failed, mark the order `FAILED`, record the idempotency failure, and abort.
4. Payment Reservation: Request fund reservation via `PaymentsFacade.reserve()`. If payment fails (e.g., insufficient funds), compensate by releasing reserved inventory via `InventoryFacade.release()`, mark the order `FAILED`, record the idempotency failure, and abort.
5. Order Confirmation & Events: Confirm payment via `PaymentsFacade.confirm()`, consume inventory reservation via `InventoryFacade.consume()`, transition order status to `CONFIRMED`, publish `OrderConfirmedEvent` via transactional outbox, publish its `OrderDto` through the same post-commit externalization path to `orders.stream`, and complete the idempotency record with the cached response.

Idempotency and Compensation Guarantees:
- Scoped Idempotency: Keys are isolated by customer: `(customer_id, idempotency_key)` to avoid cross-tenant or cross-customer collision.
- Canonical Request Fingerprinting: SHA-256 fingerprint generated from normalized customer ID, sorted item list (`code:quantity:price`), and normalized address. Replay with identical fingerprint returns original cached `OrderResponse`; replay with altered parameters returns HTTP 409 Conflict.
- Safe Retries: All compensation calls (`InventoryFacade.release`, `InventoryFacade.cancelReservation`, `PaymentsFacade.release`, `PaymentsFacade.refund`) are idempotent.
- RabbitMQ Streams: Queue `orders.stream` of type `stream` is maintained for real-time order stream consumption and queries via `GET /api/v1/orders/store`.

Recovery implementation (clarification of the saga decision):
- `TransactionTemplate` with `REQUIRES_NEW` commits the `NEW` order, price snapshot, request fingerprint, and claim before any reservation. Each Inventory/Payments mutation also commits independently. Database schemas alone do not prevent a shared local transaction; these boundaries are deliberate.
- Persist `operation_key` on the claim (`order-<orderId>` for new orders) and use it for every reservation, capture, consume, and release. Existing claims without this field retain their original reservation key. Never overwrite a failed claim's fingerprint or create a second order for that key.
- The recovery worker runs every 30 seconds (`orders.recovery.delay-ms`) and scans durable `PROCESSING`, `COMPENSATING`, and `CANCELLING` claims. A pessimistic claim lock serializes recovery, confirmation, and cancellation across instances. Matching requests while unfinished return an explicit conflict; the worker resumes the existing workflow.
- If capture commits but consume, order persistence, or event registration fails, the claim remains `PROCESSING`. Recovery replays reserve/confirm/consume using stored order items and prices. Payments returns the already confirmed payment and Inventory returns already consumed stock without repeating effects. The order transition, cached response, and durable event registrations commit together. Local rollback never reverses the captured payment.
- Reservation failures first commit order `FAILED` plus claim `COMPENSATING`. Independent compensation then releases any reserved payment and restores inventory. If compensation fails, the intent remains durable for the worker. Completion changes the claim to `FAILED`; a new checkout requires a new client key.
- Cancellation first commits `CANCELLING`. A confirmed payment is refunded with `cancel-<orderId>`; a reserved payment is released with its reservation key. `InventoryFacade.cancelReservation` releases reserved stock or restocks consumed stock under a reservation lock, recording `RELEASED` atomically. Only successful compensation allows `ROLLBACK`. A completed checkout keeps its original cached response for identical create replays after cancellation. A retry after partial compensation reuses these keys. Legacy orders lacking reservation references require reconciliation and cannot be silently marked cancelled.
- Order prices come from Catalog; submitted prices are ignored for pricing but retained in the request fingerprint for conflict detection. Recovery and replay retain the stored price snapshot.
- `OrderConfirmedEvent` routes to `order.confirmed` / `orders.events`; `OrderDto` routes to `order.created` / `orders.stream`. The existing Modulith outbox handles post-commit dispatch. Consumers replay retained messages from `first` on restart; stream retention is seven days. Older persisted orders remain available from the database. Existing stream queues must be migrated using broker-supported retention policy or coordinated redeclaration before deploying changed queue arguments. Remove obsolete `order.*` bindings during that migration; declaring new bindings does not remove existing ones.
- The stream lookup cache is bounded by `orders.stream.cache-max-size` (default 10,000). Complete listings always use database pages sorted by ID, regardless of cache hydration or eviction. Batch processing uses independent checkout transactions and preserves input positions with the existing `SERVICE_UNAVAILABLE` response for failures.
- Recovery failures are logged with the order ID and leave the claim pending. Operators must monitor pending claims and retry failures after resolving unavailable dependencies; no automatic expiry discards compensation obligations.

3. Alternatives Considered
Alternative A: One local database transaction

A single database transaction cannot span isolated schemas cleanly and cannot reverse external financial interactions or external payment gateway captures.

Alternative B: Purely choreography-driven Kafka events

The standalone microservices attempted event choreography using Kafka Streams joins. In the modular monolith, in-process public module facades eliminate network latency, serialization overhead, and race conditions, making synchronous orchestration with explicit compensation significantly simpler, more observable, and deterministic.

Alternative C: Explicit orchestration with compensation (Chosen)

This provides explicit control over workflow states (`NEW`, `CONFIRMED`, `FAILED`, `ROLLBACK`), recovery decisions, failure compensation, and idempotent retries.

4. Consequences
Positive
- Explicit, deterministic ownership of order checkout saga.
- Durable, retryable compensation when payment fails; dependency outages can delay restoration.
- Scoped idempotency prevents double charging and duplicate order submission.
- Preserves `/api/v1/orders/store` queryability through RabbitMQ Streams.
- Strict compliance with Spring Modulith module boundaries.

Trade-offs
- Compensation operations must be carefully tested and verified idempotent.
- Idempotency records require storage in `orders.order_idempotency`.

5. Implementation Guidance
- REST endpoints standardized to `/api/v1/orders` with `/api/orders` alias for backward compatibility.
- Order prices snapshotted at creation time.
- All compensation operations must be safe to retry.

6. Verification Requirements
- Successful order creation tested end-to-end.
- Catalog validation failures abort before reservation.
- Inventory reservation failure marks order `FAILED`.
- Payment reservation failure triggers inventory release compensation and marks order `FAILED`.
- Idempotent replays return cached response without repeated side effects.
- Mismatched request with same key returns HTTP 409 Conflict.
- Order cancellation triggers payment refund and transitions status to `ROLLBACK`.
- Recovery from a fresh worker after capture, retryable refund/release, pre-commit visibility, consumer restart replay, and cross-customer keys are covered by integration tests.
- Spring Modulith architectural verification verifies module DAG.

7. Approval
Reviewed by: Repository maintainers & automated test suite
Approved on: 2026-10-22
Implementation status: PASS
8. References
spec/05-orders-orchestration.md
