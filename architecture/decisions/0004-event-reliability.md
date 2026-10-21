ADR-0004: Define Event Reliability and Recovery
Status: Proposed
Date: 2026-10-21
Decision owners: Repository maintainers
Related specifications: spec/01-modulith-foundation.md, spec/05-orders-orchestration.md, spec/06-api-and-deployment.md
Supersedes: None
Superseded by: None
1. Context

Spring application events provide convenient in-process communication, but event publication does not automatically guarantee durable delivery or successful listener processing.

Some business transitions may require stronger reliability than ordinary in-process notification.

The repository may also retain Kafka integrations for external consumers.

2. Decision

Classify events according to their business importance and delivery requirements.

For each event, document:

Publisher and owning module.
Consumer and purpose.
Publication timing.
Transaction semantics.
Delivery and durability requirements.
Retry and recovery behavior.
Idempotency requirements.
Monitoring and failure visibility.

Use ordinary Spring application events when their delivery semantics satisfy the use case.

For business-critical asynchronous operations requiring reliable recovery, evaluate a transactional outbox or another durable mechanism appropriate to the system.

Retain Kafka where required by external integration contracts.

Do not treat an ordinary after-commit listener as a durable delivery guarantee.

3. Alternatives Considered
Alternative A: Synchronous module API calls

Suitable when the caller requires an immediate result, but does not replace every asynchronous integration requirement.

Alternative B: In-process application events

Simple and useful for in-process notifications, but require explicit consideration of transaction and process-failure behavior.

Alternative C: Durable event publication

An outbox or equivalent mechanism can support recovery when publication must survive process failure, at the cost of additional persistence and operational complexity.

4. Consequences
Positive
Explicit delivery expectations.
Better failure diagnosis.
Clear distinction between best-effort notification and durable business processing.
Trade-offs
Some event flows may require additional infrastructure or persistence.
Retry and duplicate-delivery handling must be designed.
Operational monitoring becomes part of the reliability contract.
5. Implementation Guidance
Avoid adding a durable messaging mechanism to every event without a requirement.
Preserve existing external Kafka contracts.
Make consumers idempotent where duplicate processing is possible.
Define poison-message handling and recovery where applicable.
Ensure event payloads contain only appropriate immutable data.
6. Verification Requirements
Event publication timing is documented.
Required durability is identified per event.
Retry and recovery paths are documented.
Duplicate processing does not duplicate business effects.
Required event failures are observable.
External messaging contracts are preserved.
7. Approval
Reviewed by: TBD
Approved on: TBD
Implementation status: Not verified
8. References
spec/01-modulith-foundation.md
spec/05-orders-orchestration.md
spec/06-api-and-deployment.md