ADR-0002: Define Cross-Module Communication
Status: Proposed
Date: 2026-10-21
Decision owners: Repository maintainers
Related specifications: spec/01-modulith-foundation.md, spec/05-orders-orchestration.md
Supersedes: None
Superseded by: None
1. Context

Independent microservices communicate across network boundaries. After migration into one process, internal business communication should avoid unnecessary HTTP calls while preserving clear contracts and transaction semantics.

2. Decision

Use direct calls to public module APIs when the caller requires an immediate result.

Use Spring application events for meaningful notifications and follow-up work where asynchronous processing is appropriate.

Do not introduce events merely to replace all method calls.

Do not use internal HTTP calls for ordinary communication between modules within the same application.

Use immutable request, response, and event payloads where appropriate.

3. Alternatives Considered
Alternative A: Retain internal HTTP calls

This preserves network-style separation but adds unnecessary operational overhead for in-process calls.

Alternative B: Direct calls for every interaction

This simplifies request-response operations but does not adequately represent every asynchronous notification.

Alternative C: Public module APIs plus application events

This distinguishes immediate business operations from appropriate event-driven work.

4. Consequences
Positive
Clear interaction contracts.
Reduced unnecessary network communication.
Easier testing of module interactions.
Trade-offs
Event consumers require explicit failure handling.
Module API changes require compatibility review.
Event delivery and transaction semantics must be documented.
5. Implementation Guidance
Keep public interfaces small and purpose-specific.
Do not expose internal persistence types.
Document event ownership and consumer responsibilities.
Preserve required Kafka contracts for external integrations.
6. Verification Requirements
Cross-module calls use approved public APIs.
Internal HTTP dependencies are removed where appropriate.
Events are used only for appropriate domain or application notifications.
Event payloads do not expose mutable persistence entities.
Transaction and failure behavior is documented.
7. Approval
Reviewed by: TBD
Approved on: TBD
Implementation status: Not verified
8. References
spec/01-modulith-foundation.md
spec/05-orders-orchestration.md