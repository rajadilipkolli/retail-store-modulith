Specification 02: Catalog Module
Status: Proposed
Depends on: spec/01-modulith-foundation.md
Phase: 2 — Catalog Migration
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
Existing API request and response formats where practical.

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

Any change to pricing behavior MUST be explicitly documented.

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

Do not migrate the entire database or change persistence technology as part of this phase unless explicitly approved.

8. REST Integration

Where the existing Catalog REST API must remain available, preserve its external contract.

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
Existing REST contracts.
Application context integration.

Add architecture tests ensuring other modules cannot access Catalog internals.

Use PostgreSQL integration testing where database-specific behavior matters.

10. Acceptance Criteria
Catalog business logic resides in the Catalog module.
Catalog owns its product persistence and repositories.
A documented public module API exists.
No other business module accesses Catalog internals.
Product identifiers and behavior remain compatible.
Pricing uses appropriate precise representations.
Existing Catalog endpoints remain compatible or changes are documented.
Liquibase behavior is tested.
Unit and integration tests pass.
Spring Modulith architecture verification passes.
The complete application builds successfully.
11. Exit Criteria

Catalog functionality MUST be usable through the modular monolith's module API.

Orders and Inventory MUST be able to integrate with Catalog without accessing its repositories or entities.

The original Catalog service MUST remain available until the overall migration is approved.

12. Implementation Rules

Implement only the Catalog migration.

Do not migrate Inventory, Payments, or Orders business logic as part of this task.

Run targeted tests and the appropriate Maven verification command. Report any remaining dependencies on the original Catalog service.
