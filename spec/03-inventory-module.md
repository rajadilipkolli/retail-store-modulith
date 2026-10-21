Specification 03: Inventory Module
Status: Proposed
Depends on: spec/01-modulith-foundation.md, spec/02-catalog-module.md
Phase: 3 — Inventory Migration
1. Objective

Migrate the existing Inventory business capability into the inventory Spring Modulith module while preserving stock management behavior, concurrency guarantees, reservation semantics, and database integrity.

Inventory MUST own stock quantities, reservations, and related business invariants.

2. Repository Assessment

Before implementation:

Inspect the existing Inventory service.
Inventory all stock-related endpoints.
Inspect inventory entities, repositories, services, and validation rules.
Inspect database constraints, indexes, and Liquibase changesets.
Identify how inventory is updated and queried.
Identify consumers of Inventory APIs.
Inspect existing Kafka events and HTTP integrations.
Identify concurrency controls and transaction boundaries.
Run the existing Inventory tests and record baseline results.

Do not assume that the existing service already implements reservations. Preserve its actual behavior and introduce additional reservation functionality only when required by the order workflow.

3. Module Structure

Use the existing root package and establish a structure similar to:

inventory/
  package-info.java
  InventoryFacade.java
  internal/
    application/
    domain/
    persistence/
    web/


The Inventory module MUST own:

Stock domain logic.
Inventory repositories and entities.
Stock adjustment operations.
Reservation and release logic where supported or required.
Inventory-specific validations.
Persistence and transaction boundaries.

Other modules MUST NOT directly access Inventory repositories or entities.

4. Public Module API

Provide a small public API for required inventory operations.

It SHOULD support:

Retrieving inventory availability.
Reserving stock for an order.
Releasing a previous reservation.
Retrieving reservation status where required.

Method signatures MUST follow the actual domain model.

Commands SHOULD identify the order or reservation using a stable identifier.

Reservation requests SHOULD include the product identifier and requested quantity.

Results MUST distinguish successful reservations from insufficient stock and other business failures.

Do not expose persistence entities through the public API.

5. Inventory Invariants

The module MUST enforce the following rules:

Available stock MUST NOT become negative.
A reservation MUST NOT exceed available stock.
Concurrent reservations MUST preserve stock integrity.
A reservation MUST be associated with the relevant business operation.
Repeated processing of the same reservation request MUST NOT reserve stock twice.
Releasing the same reservation repeatedly MUST NOT increase available stock repeatedly.
Reservation state transitions MUST be explicit.
Invalid quantities MUST be rejected.

Use database transactions and appropriate locking, atomic updates, or optimistic concurrency controls.

An in-memory check followed by an independent database update is insufficient to prevent overselling under concurrency.

6. Reservation Lifecycle

Where the business workflow requires explicit reservations, define a lifecycle appropriate to the existing model, such as:

Requested.
Reserved.
Released.
Consumed or committed, if the business model supports it.
Failed.

These are conceptual states, not mandatory enum values.

Document permitted transitions and their effects on available stock.

A reservation MUST NOT be released after it has been irreversibly consumed unless the domain explicitly supports a corresponding reversal operation.

Do not introduce expiration or automatic release behavior without defining its timing and consistency implications.

7. Transaction and Concurrency Requirements

Requirements:

Inventory changes MUST be transactional.
Concurrent reservations MUST be safe.
Reservation and release operations MUST be idempotent.
Database constraints SHOULD reinforce critical invariants.
Failed operations MUST leave inventory in a consistent state.
Transaction isolation and locking behavior MUST be documented.
Tests MUST cover simultaneous requests for limited stock.

If an order workflow reserves stock before payment completes, Inventory MUST support a defined release or compensation path.

Do not assume that a transaction spanning the entire modular monolith automatically makes all independently committed operations atomic.

8. Integration With Catalog and Orders

Inventory MAY use Catalog's public API for product validation where required.

Inventory MUST NOT access Catalog repositories or entities.

Orders MUST use Inventory's public API for stock operations.

Inventory MUST NOT depend on Orders' internal implementation.

If an event is required to communicate inventory outcomes, publish an immutable event containing the necessary business identifiers and outcome.

Do not introduce event-based communication when the caller requires an immediate reservation result.

9. Persistence and Liquibase

Requirements:

Preserve existing stock records and identifiers.
Preserve existing database constraints and relevant indexes.
Maintain Liquibase as the schema migration mechanism.
Add new changesets for schema changes.
Avoid destructive modifications to applied changesets.
Test database initialization and upgrades.
Verify data integrity after migration.

Do not silently reset stock quantities or recreate existing inventory data.

10. Testing Requirements
Unit tests

Cover:

Stock availability.
Successful reservation.
Insufficient stock.
Invalid quantities.
Reservation release.
Duplicate reservation requests.
Duplicate release requests.
Invalid reservation transitions.
Integration tests

Cover:

Persistence and transaction rollback.
Database constraints.
Concurrent reservation attempts.
Reservation and release idempotency.
Liquibase initialization.
Public module API behavior.
Architecture tests

Verify that other modules cannot access Inventory internals and that the module dependency graph remains valid.

Use PostgreSQL Testcontainers where practical for concurrency and database-specific behavior.

11. Acceptance Criteria
Inventory logic resides in the Inventory module.
Inventory owns its persistence.
A documented public API exists.
Stock cannot become negative.
Concurrent reservations cannot oversell stock.
Reservation and release operations are idempotent.
Reservation transitions are validated.
Compensation behavior is defined where required.
Existing inventory data is preserved.
Liquibase migrations are tested.
Unit, integration, and architecture tests pass.
No other module accesses Inventory internals.
The application builds successfully.
12. Exit Criteria

Inventory can be used through its public module API, including the stock operations required by Orders.

The original Inventory service MUST remain available until the complete migration has been approved.

13. Implementation Rules

Implement only the Inventory migration.

Do not implement Orders orchestration or migrate Payments in this phase.

If the existing application lacks a reservation concept, document the proposed behavior and its compatibility implications before implementing it.

Run targeted tests and the complete applicable Maven verification command. Report concurrency test results and any remaining dependencies on the original Inventory service.
