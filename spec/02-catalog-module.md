---
title: "Specification 02: Catalog Module"
status: "Proposed"
depends_on: "spec/01-modulith-foundation.md"
phase: "2 — Catalog Migration"
---

Specification 02: Catalog Module
1. Objective

Migrate the existing Catalog business capability into the catalog Spring Modulith module while preserving existing product behavior, persistence, validations, and externally consumed API contracts.

The Catalog module MUST become the sole owner of its product domain logic and persistence within the new application.

2. Repository Assessment

Before implementation:

Inspect the existing Catalog service.
Inventory all product-related REST endpoints.
Identify request and response DTOs.
Inspect product entities, repositories, services, and validation rules.
Inspect Liquibase changesets and database constraints.
Identify consumers of the Catalog API.
Identify dependencies on Inventory, Orders, and other services.
Run existing Catalog tests and record their baseline results.

Preserve existing business semantics. Do not invent new product fields or operations without evidence or approval.

3. Module Structure

Use the existing root package and establish a structure similar to:

catalog/
  package-info.java
  CatalogFacade.java
  ProductQuery.java
  internal/
    application/
    domain/
    persistence/
    web/


Adapt names and packages to the actual codebase.

The catalog module MUST own:

Product domain logic.
Product persistence.
Product repositories.
Catalog-specific validation.
Catalog application services.
Public product query interfaces.

Other modules MUST NOT directly access Catalog entities or repositories.

Catalog MUST NOT depend on Orders, including its public APIs and events.

4. Public Module API

Provide a small public API for required cross-module operations.

The API SHOULD support:

Retrieving product information by its existing identifier.
Validating that a product exists and can be ordered.
Returning the price and product information required by order creation.

These are requirements, not mandatory method signatures.

Use immutable DTOs or records where appropriate.

The public API MUST NOT expose JPA entities, lazy-loaded relationships, persistence sessions, or repository interfaces.

Avoid introducing unnecessary abstractions for operations that have no actual consumer.

5. Product Behavior

The migration MUST preserve:

Existing product identifiers and product-code semantics.
Product descriptions and supported attributes.
Existing product lookup behavior.
Existing validation rules.
Existing filtering and pagination.
Existing product availability semantics, if implemented by Catalog.
Exact compatibility of existing request and response formats for every externally consumed Catalog endpoint. Deviations require an explicitly approved, versioned exception documenting the affected endpoint, consumer impact, and approver.

Catalog MUST NOT become responsible for inventory reservations merely because product availability is queried during order creation.

Stock ownership belongs to Inventory.

6. Pricing

Product prices MUST use precise monetary representations, such as BigDecimal, where appropriate to the existing domain.

Requirements:

Preserve currency and scale semantics.
Avoid floating-point monetary calculations.
Preserve existing price validation.
Provide a stable price value for order-time price snapshots.
Do not allow Orders to modify Catalog persistence directly.

The order workflow MUST use the agreed price at order creation time rather than relying on future catalog price changes.

Any change to pricing behavior MUST be explicitly documented and approved. Changes affecting an externally consumed contract also require the versioned exception defined in Section 5.

7. Persistence and Liquibase

Catalog MUST own its persistence logic.

Requirements:

Preserve existing schemas and product identifiers where practical.
Preserve applicable database constraints.
Reuse existing Liquibase changesets when compatible.
Add new changesets rather than destructively modifying applied changesets.
Test initialization from an empty database.
Test compatibility with representative existing data.
Avoid introducing cross-module foreign keys without justification.
Product entities MUST use TSID (Time-Sorted Unique Identifiers) for primary keys (id BIGINT) via @Id @Tsid(TsidSupplier.class) from hypersistence-utils-hibernate-73 configured with com.example.retail.shared.persistence.TsidSupplier, avoiding database sequence round-trips.

Do not migrate the entire database or change persistence technology as part of this phase unless explicitly approved.

8. REST Integration

Every externally consumed Catalog REST endpoint MUST preserve its exact external contract, including routes, methods, request and response formats, validation, status codes, errors, security, filtering, and pagination. Deviations require the explicitly approved, versioned exception defined in Section 5.

The new application MUST route requests to the Catalog module's controller or adapter without introducing unnecessary internal HTTP calls.

Internal module APIs MUST remain separate from external REST endpoints.

Do not expose internal DTOs automatically as public API contracts.

9. Testing Requirements

Add unit tests for:

Product lookup.
Product validation.
Existing business rules.
Missing products.
Invalid requests.
Price handling where applicable.

Add integration tests for:

Persistence operations.
Liquibase initialization.
Public module API behavior.
Exact compatibility for every externally consumed REST contract and each explicitly approved, versioned exception.
Catalog cutover and rollback routing, write fencing, data reconciliation, and order-time price consistency.
Application context integration.

Add architecture tests ensuring other modules cannot access Catalog internals and Catalog cannot depend on Orders.

Use PostgreSQL integration testing where database-specific behavior matters.

10. Acceptance Criteria
Catalog business logic resides in the Catalog module.
Catalog owns its product persistence and repositories.
A documented public module API exists.
No other business module accesses Catalog internals.
Product identifiers and behavior remain compatible.
Pricing uses appropriate precise representations.
Every externally consumed Catalog endpoint remains exactly compatible unless covered by an explicitly approved, versioned exception.
Catalog coexistence, cutover, and rollback enforce a single product write owner and consistent order-time prices.
Liquibase behavior is tested.
Unit and integration tests pass.
Spring Modulith architecture verification passes.
The complete application builds successfully.
11. Exit Criteria

Catalog functionality MUST be usable through the modular monolith's module API.

Orders and Inventory MUST be able to integrate with Catalog without accessing its repositories or entities.

The original Catalog service MUST remain available for reads until the overall migration is approved. Write ownership MUST follow this coexistence contract:

Before cutover, the original Catalog service and its product store are authoritative and are the sole product write owner. The new module may serve verified shadow reads but MUST NOT accept production product writes or supply unverified prices to live orders.
At the explicitly approved cutover, fence legacy writes, drain in-flight mutations and affected order creation, transfer and reconcile product data and prices, then designate the Catalog module and its product store as the sole authority. If the physical store is retained, transfer exclusive write access instead of copying it.
After cutover, route all product mutations, external reads, and order-time product/price lookups to the module. Retained legacy read endpoints MUST proxy to the module's public query API; they MUST NOT serve a stale independent store. Legacy mutation routes MUST be routed to the module or rejected before execution.
Uncontrolled dual writes are prohibited. Application credentials, routing, background jobs, event consumers, and administrative paths MUST enforce exactly one product write owner at a time.
Document routing changes, write fencing, reconciliation evidence, monitoring, rollback triggers, and the cutover owner before enabling production traffic.
Rollback MUST first fence module writes and drain affected operations, reconcile all post-cutover product changes into the legacy store, and verify product identifiers and prices before transferring sole write ownership and routing back to the original service. If reconciliation cannot be proven, keep writes fenced and block rollback.
Existing orders MUST retain their agreed price snapshots throughout cutover and rollback. New orders MUST obtain prices only from the current authoritative Catalog API.

12. Implementation Rules

Implement only the Catalog migration.

Do not migrate Inventory, Payments, or Orders business logic as part of this task.

Run targeted tests and the appropriate Maven verification command. Report any remaining dependencies on the original Catalog service.
