ADR-0003: Define Order Processing and Compensation
Status: Proposed
Date: 2026-10-21
Decision owners: Repository maintainers
Related specifications: spec/03-inventory-module.md, spec/04-payments-module.md, spec/05-orders-orchestration.md
Supersedes: None
Superseded by: None
1. Context

Order processing involves multiple business operations, including product validation, inventory reservation, payment authorization, and order persistence.

These operations may commit independently. A failure in a later operation cannot automatically undo earlier database commits or external side effects.

2. Decision

The Orders module owns order lifecycle management and coordinates the order-processing workflow through the public APIs of Catalog, Inventory, and Payments.

Before implementation is finalized, the workflow MUST explicitly define:

Operation ordering.
Transaction boundaries.
Order state transitions.
Inventory reservation and release.
Payment authorization, capture, release, and refund behavior as applicable.
Idempotency keys and duplicate request handling.
Failure compensation.
Retry and recovery behavior.

No particular operation sequence is considered approved by this proposed ADR until it is reviewed against the existing application behavior and business requirements.

3. Alternatives Considered
Alternative A: One local database transaction

A single transaction cannot reliably reverse all external side effects and is unsuitable when operations have independently committed or external financial effects.

Alternative B: Uncoordinated service calls

This makes partial failures and recovery difficult to reason about.

Alternative C: Explicit orchestration with compensation

This provides a place to define workflow state, recovery decisions, and idempotent retries.

4. Consequences
Positive
Explicit ownership of order processing.
Documented failure and recovery paths.
Easier testing of business invariants.
Trade-offs
More workflow state and recovery logic may be required.
Compensation operations must be safe to retry.
Operational monitoring must detect stuck workflows.
5. Implementation Guidance
Inspect the original Order service before selecting the final sequence.
Preserve approved external API behavior.
Use stable operation identifiers where required.
Persist workflow progress when required for recovery.
Avoid duplicate charges, duplicate refunds, and repeated stock releases.
Document which operations are synchronous and which require asynchronous recovery.
6. Verification Requirements
Successful order creation is tested.
Duplicate requests are tested.
Inventory reservation failures are tested.
Payment failures are tested.
Order persistence failures are tested.
Compensation operations are idempotent.
Final persisted order, inventory, and payment states are verified.
Retry and recovery behavior is documented.
7. Approval
Reviewed by: TBD
Approved on: TBD
Implementation status: Not verified
8. References
spec/05-orders-orchestration.md