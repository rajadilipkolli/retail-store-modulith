---
title: "Specification 04: Payments Module"
status: "Proposed"
depends_on: "spec/01-modulith-foundation.md"
phase: "4 — Payments Migration"
---

Specification 04: Payments Module
1. Objective

Migrate the existing Payment service's customer account and payment-related business capabilities into the payments Spring Modulith module.

Preserve existing business rules, financial invariants, API behavior, and persistence semantics.

The module MUST prevent duplicate financial effects and support the payment operations required by the order workflow.

2. Repository Assessment

Before implementation:

Inspect the existing Payment service.
Identify whether it handles account balances, payment authorization, simulated payments, or external payment providers.
Inventory all payment and customer-related endpoints.
Inspect entities, repositories, services, and transaction boundaries.
Inspect validation rules and database constraints.
Identify consumers of payment APIs and events.
Inspect existing Kafka producers and consumers.
Run the existing Payment tests and record baseline results.

Do not assume that the existing service implements a real external payment provider.

Preserve the actual payment model and explicitly document any proposed extensions.

3. Module Structure

Use the existing root package and establish a structure similar to:

payments/
  package-info.java
  PaymentsFacade.java
  internal/
    application/
    domain/
    persistence/
    web/


The Payments module MUST own:

Customer account logic currently belonging to the Payment service.
Payment domain logic.
Payment entities and repositories.
Financial state transitions.
Payment-specific validation.
Payment persistence and transactions.
Payment audit information where applicable.

Other modules MUST NOT directly access Payments repositories or entities.

4. Public Module API

Expose a small API for the operations required by the application.

Depending on the existing domain, this MAY include:

Customer eligibility validation.
Payment authorization.
Funds reservation.
Payment capture.
Reservation release.
Refund processing.
Payment status queries.

Do not implement unsupported financial operations merely because they appear in this list.

Use immutable command and result types where appropriate.

The public API MUST distinguish business failures, such as insufficient funds, from unexpected technical failures.

5. Financial Invariants

The module MUST preserve financial correctness.

Requirements:

Monetary calculations MUST use precise types such as BigDecimal.
Currency MUST be represented and validated appropriately.
Duplicate requests MUST NOT create duplicate financial effects.
Payment state transitions MUST be explicit.
Invalid payment transitions MUST be rejected.
Financial changes MUST be transactional within their persistence boundary.
Financial operations MUST be auditable.
Sensitive payment information MUST NOT be unnecessarily logged.
Existing customer identifiers and account semantics MUST be preserved.

Where the existing model maintains balances, enforce its actual overdraft, reservation, and available-balance rules.

Do not introduce new financial assumptions without approval.

Canonical money policy

Before implementing the migration, document one canonical money policy using evidence from the original Payment service, its schemas, API contracts, and tests. The policy MUST specify:

The exact decimal scale for each supported currency, including input precision validation and persisted/API amount representation.
The named rounding mode and the calculation stages at which rounding occurs, including the treatment of excess input precision and rounding ties.
The explicit supported currency codes and the rejection behavior for unsupported or missing currencies; if the legacy model has an implicit currency, identify it from verified evidence.
Currency-mismatch handling between requests, accounts, authorizations, captures, and refunds: mismatches MUST be rejected without mutation unless covered by an explicitly approved conversion rule.
Whether conversion is supported. Implicit conversion is prohibited. Any approved conversion MUST define permitted currency pairs, rate source, rate timestamp, precision, rounding, and recorded source/target amounts; otherwise cross-currency operations MUST be rejected.

Apply this policy consistently to every supported authorization, reservation, capture/debit, release, refund, and ledger/available-balance calculation. Refunds MUST reference the original transaction's currency and approved conversion details where applicable.

This specification does not establish unverified scale, rounding, or currency values. If the original service has no established policy, or its rules are inconsistent, record a proposed policy and obtain named approval from the responsible financial/business owner in an ADR before migrating. Record the approver, date, exact policy values, and compatibility impact; implementation and phase completion remain blocked until the policy is verified or approved. Changes to established policy also require explicit approval.

6. Payment Lifecycle

Where supported by the domain, distinguish operations such as:

Authorization.
Reservation.
Capture or debit.
Release or reversal.
Refund.

These operations MUST NOT be treated as interchangeable.

Document the permitted state transitions and their effects on the account or payment record.

A refund MUST NOT automatically be implemented as an arbitrary balance increment without validating the original transaction and refund eligibility.

7. Idempotency and Duplicate Processing

Payment commands that can be retried MUST have a documented duplicate-processing strategy.

Requirements:

Identify operations using stable business identifiers.
Use an idempotency key or equivalent unique operation identifier where appropriate.
Enforce uniqueness at the persistence layer where necessary.
Ensure concurrent duplicate requests cannot create duplicate financial effects.
Return or reconstruct the result of an already completed operation when appropriate.
Distinguish a retry of the same operation from a legitimate new operation.

An in-memory cache alone MUST NOT be relied upon for financial idempotency.

8. Transactions and Order Integration

Orders MUST use the Payments public API.

Payments MUST NOT depend on Orders, including its public APIs and events.

Payments MUST NOT access Orders' internal packages.

If the workflow reserves funds before order confirmation, define the release or compensation behavior when the order fails.

Do not keep a database transaction open across an unnecessary external network call.

If a real payment provider is present, document its timeout, retry, idempotency, and reconciliation requirements.

Do not claim that local database rollback can reverse an external financial operation.

9. Persistence and Liquibase

Requirements:

Preserve existing customer and payment records.
Preserve identifiers and financial state.
Maintain existing applicable database constraints.
Use Liquibase for schema changes.
Add new changesets instead of destructively editing applied changesets.
Test initialization from an empty database.
Test upgrades from representative existing data.
Document backup and rollback procedures.

Do not reset account balances or payment records during migration.

10. Security and Privacy

Requirements:

Preserve existing authentication and authorization behavior.
Do not expose sensitive account information through internal DTOs unnecessarily.
Do not log credentials, secrets, or payment-sensitive data.
Avoid exposing internal exception details.
Apply appropriate access controls to payment and customer operations.

Any change to security configuration MUST be explicitly documented and tested.

11. Testing Requirements
Unit tests

Cover:

Customer eligibility.
Payment authorization success and failure.
Insufficient funds where applicable.
Valid and invalid state transitions.
Reservation and release behavior.
Duplicate requests.
Refund eligibility where supported.
The canonical money policy for every supported operation: accepted scale, excess precision, rounding ties and boundaries under the chosen rounding mode, supported/unsupported/missing currencies, and currency mismatches with no mutation.
Rejection of conversions when unsupported, or approved currency pairs, rate source/timestamp, precision, rounding, and audit amounts when conversion is supported.
Consistent authorization, capture, refund, and balance results for the same monetary inputs, including legacy behavior fixtures.
Integration tests

Cover:

Transaction rollback.
Concurrent duplicate requests.
Persistence constraints.
Financial idempotency.
Canonical money policy across API input, persistence round trips, financial operations, and balance calculations.
Single write ownership and read routing through Payment cutover and rollback, including late retries and event consumers.
Liquibase initialization and upgrades.
Public module API behavior.
Failure tests

Verify that:

A failed operation does not leave inconsistent local financial state.
A retry does not duplicate a completed financial effect.
Failed order processing follows the documented compensation policy.
External payment timeouts, if applicable, are handled safely.
12. Acceptance Criteria
Payment and customer account behavior is migrated.
Payments owns its persistence.
A documented public module API exists.
The canonical money policy is verified against existing behavior or explicitly approved before migration, and every policy rule has passing tests.
Monetary calculations use appropriate precise representations.
Payment coexistence and rollback enforce one write owner for migrated operations.
Financial state transitions are validated.
Duplicate financial operations are prevented.
Transaction and compensation behavior is documented.
Existing customer and payment data is preserved.
Security behavior is preserved.
Unit and integration tests pass.
Architecture verification passes.
The application builds successfully.
13. Exit Criteria

The Payments module can be used through its public API without accessing the original Payment service internally.

The module supports the payment operations required by the future Orders orchestration phase.

The original Payment service MUST remain available for reads until the overall migration has been approved. Before cutover it remains the sole write owner; the module MUST NOT accept production mutations for the same accounts or payment operations.

At the explicitly approved cutover, fence legacy writes and drain or reconcile in-flight operations, then transfer and reconcile financial state, audit history, and idempotency records before enabling module writes. The Payments module MUST be the single write owner for migrated operations and their affected accounts/balances. Legacy reads for migrated data MUST delegate to its public query API; legacy mutation routes MUST be routed to the module or rejected before execution.

Routing, credentials, scheduled jobs, event consumers, callbacks, and late retries MUST enforce this ownership. The legacy service is read-only for migrated operations; dual writes are prohibited. Any retained legacy write operations MUST affect disjoint, explicitly documented accounts and financial state.

Document ownership, routing, reconciliation evidence, cutover/rollback triggers, and the responsible owner before cutover. Rollback MUST fence module writes, reconcile all post-cutover financial changes and idempotency records, verify balances and external provider outcomes where applicable, then transfer exclusive ownership and routing back to the legacy service. Block rollback if reconciliation cannot be proven; application rollback alone MUST NOT replay or reverse financial effects.

14. Implementation Rules

Implement only the Payments migration.

Do not migrate Orders orchestration or retire the original Payment service in this phase.

Document any differences between the existing financial model and the proposed order workflow before implementing additional operations.

Run targeted tests and the appropriate Maven verification command. Report any remaining dependencies on the original Payment service.
