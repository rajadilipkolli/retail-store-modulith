Specification 04: Payments Module
Status: Proposed
Depends on: spec/01-modulith-foundation.md
Phase: 4 — Payments Migration
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

Payments MUST NOT depend on Orders' internal packages.

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
Integration tests

Cover:

Transaction rollback.
Concurrent duplicate requests.
Persistence constraints.
Financial idempotency.
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
Monetary calculations use appropriate precise representations.
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

The original Payment service MUST remain available until the overall migration has been approved.

14. Implementation Rules

Implement only the Payments migration.

Do not migrate Orders orchestration or retire the original Payment service in this phase.

Document any differences between the existing financial model and the proposed order workflow before implementing additional operations.

Run targeted tests and the appropriate Maven verification command. Report any remaining dependencies on the original Payment service.
