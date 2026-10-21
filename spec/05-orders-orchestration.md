---
title: "Specification 05: Orders Module and Orchestration"
status: "Proposed"
depends_on: "spec/01-modulith-foundation.md, spec/02-catalog-module.md, spec/03-inventory-module.md, spec/04-payments-module.md"
phase: "5 — Order Migration"
---

Specification 05: Orders Module and Orchestration
1. Objective

Migrate the existing Order service into the orders Spring Modulith module and implement a clearly defined order-processing workflow using the public APIs of Catalog, Inventory, and Payments.

Preserve existing order behavior, API contracts, persistence semantics, and business invariants.

The Orders module MUST own order lifecycle management and orchestration.

2. Repository Assessment

Before implementation:

Inspect the existing Order service.
Inventory all order-related endpoints and DTOs.
Inspect order entities, repositories, services, and status transitions.
Inspect the existing order creation and cancellation workflows.
Identify all synchronous HTTP calls and Kafka interactions.
Inspect inventory and payment failure handling.
Inspect database constraints and Liquibase changesets.
Identify idempotency and retry behavior.
Run existing Order tests and record baseline results.

The existing implementation is the starting point for the migration. Do not replace its business workflow with a generic checkout example without documenting and approving the differences.

3. Module Structure

Use the existing root package and establish a structure similar to:

orders/
  package-info.java
  OrdersFacade.java
  internal/
    application/
    domain/
    persistence/
    web/


The Orders module MUST own:

Order domain logic.
Order entities and repositories.
Order items and price snapshots.
Order status transitions.
Order creation and cancellation.
Cross-module workflow orchestration.
Order-related application events.
Order-specific validations.

Catalog, Inventory, and Payments MUST NOT depend on Orders, including its public APIs and events.

Other modules MUST NOT access Orders internals.

4. Public Module API

Expose the operations required by external adapters and other legitimate consumers.

These MAY include:

Creating an order.
Retrieving an order.
Listing orders where supported.
Cancelling an order where supported.

Retrieve, list, and cancel operations MUST enforce the caller's authorized customer or tenant scope.
Create operations MUST verify that the caller may create an order for the supplied customer or tenant.
External adapters MUST pass the authorized caller context to `OrdersFacade`, and `OrdersFacade` MUST NOT trust an unvalidated customer identifier.

Preserve existing API semantics.

The public API MUST NOT expose persistence entities or repository interfaces.

Use immutable request and response DTOs where appropriate.

5. Order Domain Requirements

The order model MUST preserve the business data used by the existing application.

Where applicable, this includes:

Order identifier.
Customer identifier.
Product identifiers.
Item quantities.
Price snapshots.
Currency.
Order status.
Creation and update timestamps.
Relevant delivery information.
Payment and inventory operation references.

Do not add fields without a business requirement.

Price snapshots

Order items MUST retain the agreed price at order creation time.

A later catalog price change MUST NOT silently change the price of an existing order.

State transitions

Preserve existing statuses where they are part of the current contract.

Document valid state transitions and reject invalid transitions.

Do not assume that all orders follow the same lifecycle if the existing application supports different workflows.

6. Order Creation Workflow

Implement a workflow based on the actual business requirements.

The workflow MUST address:

Request validation.
Customer eligibility validation.
Product validation and price retrieval.
Inventory availability and reservation.
Payment authorization or reservation.
Order persistence.
Order confirmation.
Failure compensation.
Idempotent retry handling.

The exact sequence MUST be documented in an architecture decision record.

Do not assume that every step can be completed in one database transaction.

Required consistency behavior

The workflow MUST define what happens when:

A product does not exist.
A customer is ineligible.
Stock is insufficient.
Payment authorization fails.
Order persistence fails.
A request times out after processing has started.
A retry arrives after the order has been created.
Inventory is reserved but payment fails.
Funds are reserved but the order cannot be confirmed.
An event handler fails after the order transaction commits.

When a previously completed operation must be undone, use the relevant module API and a documented compensation policy.

Compensation operations MUST themselves be idempotent.

7. Idempotency

Order creation MUST support safe retry behavior.

Requirements:

Order creation MUST use an idempotency key or equivalent stable request identifier.
Bind each key to a documented scope consisting of the authorized customer identifier and, where applicable, tenant identifier for the create-order operation. Derive that scope from validated caller context; never trust an unvalidated customer or tenant identifier.
Persist a versioned canonical request fingerprint with the scoped key. Document the canonicalization algorithm and all business-relevant fields, including product identifiers, quantities, supplied currency/prices where applicable, and delivery details. Normalize equivalent representations without dropping meaningful differences. Exclude transport metadata and server-generated values; retries MUST reuse the original price snapshot rather than reprice an existing order.
Enforce uniqueness of (scope, key) in persistent storage and atomically claim the key and fingerprint before business side effects. Concurrent identical requests MUST share the same operation and MUST NOT repeat inventory or payment effects.
On same-scope key reuse, compare the canonical request fingerprint. Identical requests MUST return the original order/result; while processing, return the documented in-progress response without starting another workflow.
A different fingerprint for the same scoped key MUST return an explicit idempotency conflict (HTTP 409 with a documented error code at REST boundaries), without creating another order, mutating the original order, or performing inventory/payment operations.
The same key used by different authorized customers or tenants belongs to independent scopes and MUST NOT reveal or reuse another scope's order/result. Normal authorization checks still apply to every request and replay.
A legitimate new order in the same scope MUST use a new key.
Document the scope, canonicalization/fingerprint version, duplicate/in-progress responses, and conflict response for external API consumers.

Do not rely solely on in-memory state to prevent duplicate order creation.

8. Cross-Module Communication

Orders MUST use the public APIs of Catalog, Inventory, and Payments.

The module MUST NOT:

Import another module's internal package.
Access another module's repository.
Manipulate another module's persistence entities.
Use internal HTTP calls for operations that can safely use in-process APIs.

Use direct module API calls for operations requiring immediate success or failure information.

Use Spring application events for appropriate notifications and follow-up actions.

9. Order Events

Publish immutable events for meaningful domain transitions where required.

Potential examples include:

OrderPlaced
OrderConfirmed
OrderCancelled
OrderProcessingFailed

Only implement events that correspond to actual domain transitions and have legitimate consumers.

Events MUST contain the necessary identifiers and MUST NOT expose mutable persistence entities.

Document:

Publication timing.
Transaction semantics.
Consumer responsibilities.
Retry behavior.
Idempotency.
Failure recovery.

If an event represents a committed order transition, consumers MUST NOT observe that transition as committed before the relevant transaction succeeds.

Use durable publication and recovery mechanisms when the business requires reliable asynchronous delivery.

10. Cancellation and Compensation

Define cancellation behavior based on the actual business model.

Where applicable, cancellation MUST:

Validate whether the current order state permits cancellation.
Release inventory reservations that remain releasable.
Release or reverse payment authorization as appropriate.
Avoid duplicate refunds or financial reversals.
Persist the resulting order state.
Recover safely from partial failures.

Do not automatically refund captured payments or release consumed inventory without verifying that the operation is permitted.

Document which operations are synchronous and which require asynchronous recovery.

11. Persistence and Transactions

Orders MUST own its persistence.

Requirements:

Preserve existing order identifiers and data.
Maintain applicable database constraints.
Preserve historical price snapshots.
Use Liquibase for schema changes.
Avoid destructive modifications to applied changesets.
Define explicit transaction boundaries.
Test rollback behavior.
Test concurrent order creation where relevant.

Do not assume that one application process automatically guarantees atomicity across independently committed business operations.

12. Testing Requirements
Unit tests

Cover:

Request validation.
Order creation rules.
Price snapshot behavior.
State transitions.
Cancellation rules.
Duplicate request handling.
Compensation decisions.
Integration tests

Cover:

Public module API calls.
Persistence and transaction behavior.
Liquibase initialization.
Application event publication.
Retry and idempotency behavior, including concurrent identical requests, same-key different payloads rejected with no side effects, canonical-equivalent representations, and independent reuse across authorized customers/tenants.
Persistence of the scoped key, fingerprint, and original result across process restart.
End-to-end tests

Cover:

Successful order creation.
Invalid product.
Ineligible customer.
Insufficient inventory.
Payment authorization failure.
Order persistence failure.
Same-scope same-key identical requests returning the same order and original price snapshot.
Same-scope same-key differing requests returning HTTP 409 and leaving order, inventory, and payment state unchanged.
Same-key reuse across authorized customers/tenants creating independent orders without cross-scope result disclosure; unauthorized scope selection is rejected.
Cancellation.
Inventory release after failure.
Payment compensation after failure.
Event processing failure and recovery.
Existing REST API compatibility.

Tests MUST verify final inventory, payment, and order states, not merely HTTP response codes.

13. Acceptance Criteria
Order logic resides in the Orders module.
Orders owns its persistence.
Public module APIs are used for all cross-module interactions.
Existing business workflow is preserved or intentionally revised.
Order state transitions are explicit.
Order-time prices are preserved.
Scoped idempotency keys and canonical fingerprints prevent duplicate orders and side effects; mismatched reuse conflicts explicitly and cross-customer reuse remains isolated.
Failure and compensation paths are defined.
Application events have documented transaction semantics.
Inventory and payment operations remain consistent after failures.
Unit, integration, and end-to-end tests pass.
Spring Modulith architecture verification passes.
The application builds successfully.
14. Exit Criteria

The main commerce workflow runs entirely within the modular monolith without requiring separate Catalog, Inventory, Payment, or Order processes for internal business calls.

All required failure paths have tests and documented recovery behavior.

The original Order service MUST remain available until migration approval.

15. Implementation Rules

Implement only the Orders migration and the integration required for the four business modules.

Do not retire original services, remove production data, or change external API contracts without approval.

Run targeted tests and the full Maven verification command. Report unresolved workflow risks explicitly.
