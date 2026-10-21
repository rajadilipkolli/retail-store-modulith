---
title: "Specification: Migrate Spring Boot Microservices to Spring Modulith"
status: "Proposed"
type: "Architecture Migration"
approach: "Specification-Driven Development (SDD)"
repository: "rajadilipkolli/spring-boot-microservices-series-v2"
target_architecture: "Spring Boot Modular Monolith"
initial_java_baseline: "Java 21, subject to repository verification"
build_system: "Maven"
---

Specification: Migrate Spring Boot Microservices to Spring Modulith
Migration phases (in dependency order):

1. Foundation: spec/01-modulith-foundation.md
2. Catalog: spec/02-catalog-module.md
3. Inventory: spec/03-inventory-module.md
4. Payments: spec/04-payments-module.md
5. Orders: spec/05-orders-orchestration.md
6. API and deployment: spec/06-api-and-deployment.md
7. Migration verification: spec/07-migration-verification.md

1. Objective

Transform the existing retail microservices application into a modular monolith using Spring Boot and Spring Modulith.

The migration MUST preserve existing business capabilities, data integrity, security, and externally observable API behavior unless an intentional change is documented and approved.

The resulting application MUST:

Run Catalog, Inventory, Orders, and Payments in a single Spring Boot application.
Establish explicit business module boundaries.
Enforce module boundaries through automated architecture tests.
Replace internal HTTP communication with in-process module APIs where appropriate.
Replace internal Kafka messaging with Spring application events where appropriate.
Preserve existing business rules, validation, persistence, and API contracts.
Support reliable transaction handling, retries, idempotency, and failure recovery.
Support automated testing, database migrations, observability, and local development.
Allow individual modules to be extracted into separate services in the future if required.

The migration MUST be incremental, test-driven, and reversible wherever practical.

2. Existing System Assessment

The repository contains or has historically included the following applications:

catalog-service
inventory-service
order-service
payment-service
api-gateway
config-server
service-registry
retail-store-webapp

The existing implementation uses Spring technologies, Maven, PostgreSQL, Liquibase, and Kafka.

Before implementation, the coding agent MUST inspect the current repository to verify the actual applications, framework versions, persistence technologies, APIs, and dependencies.

The following are authoritative sources for the migration:

Existing source code.
Existing automated tests.
Existing API contracts and documentation.
Existing database schemas and Liquibase changelogs.
Existing event producers, consumers, and message contracts.
Existing configuration and deployment files.

Do not assume that the repository's current implementation exactly matches its README or historical architecture.

3. Scope
3.1 In scope
Create a new Spring Boot application for the modular monolith.
Implement Catalog, Inventory, Orders, and Payments as business modules.
Establish explicit module APIs.
Enforce module dependency boundaries.
Replace unnecessary internal HTTP calls.
Introduce Spring application events where asynchronous communication is appropriate.
Preserve business workflows and validation.
Integrate existing persistence and Liquibase migrations.
Preserve or deliberately adapt existing REST APIs.
Implement architecture, unit, integration, and end-to-end tests.
Update local development, configuration, and deployment documentation.
Provide migration and rollback procedures.
3.2 Out of scope

Unless explicitly approved, the migration MUST NOT:

Rewrite the frontend.
Introduce a new frontend framework.
Replace PostgreSQL.
Introduce event sourcing or CQRS solely for architectural reasons.
Introduce unnecessary infrastructure.
Rewrite all domain models without a clear requirement.
Remove existing security controls.
Delete the original microservices before the replacement is verified.
Change public API semantics without documenting the impact.
Claim external payment processing if the existing implementation uses a simulated account-balance model.
4. Target Architecture

The target application MUST have one Spring Boot entry point and four primary business modules:

catalog
inventory
orders
payments

The retail web application MAY remain a separate application.

External consumers MUST access the modular monolith through documented REST endpoints or other explicitly approved interfaces.

The API Gateway, Config Server, and Service Registry MUST be assessed individually. They SHOULD be removed from the new application's runtime dependencies when they no longer provide required functionality.

Kafka MAY remain for integrations that require cross-process communication or independent consumers.

5. Module Structure

Use the existing root package where practical. The following structure is illustrative:

com.example.retail
  RetailApplication.java
  catalog/
    package-info.java
    CatalogFacade.java
    ProductQuery.java
    internal/
      application/
      domain/
      persistence/
      web/
  inventory/
    package-info.java
    InventoryFacade.java
    internal/
      application/
      domain/
      persistence/
      web/
  orders/
    package-info.java
    OrdersFacade.java
    internal/
      application/
      domain/
      persistence/
      web/
  payments/
    package-info.java
    PaymentsFacade.java
    internal/
      application/
      domain/
      persistence/
      web/
  shared/
    configuration/
    security/
    observability/


The actual package names MUST be adapted to the repository.

Each business module MUST own its:

Domain model.
Business rules.
Application services.
Persistence entities and repositories.
Database access logic.
Public module API.
Relevant business events.
Module-specific tests.

The internal packages MUST NOT be imported by other business modules.

Avoid globally shared service, repository, controller, or entity packages that allow unrestricted cross-module access.

6. Module Boundaries and Dependencies
6.1 Catalog module

Responsibilities:

Maintain product information.
Provide product lookup and query operations.
Validate product existence for order processing.
Preserve existing product identifiers, descriptions, prices, and other catalog fields.
Preserve existing filtering and pagination behavior where implemented.

Requirements:

Catalog MUST own product persistence.
Product identifiers MUST remain stable wherever existing workflows depend on them.
Monetary values MUST use precise representations such as BigDecimal where appropriate.
Catalog MUST NOT depend on Orders, Inventory, or Payments implementation packages.
6.2 Inventory module

Responsibilities:

Maintain stock quantities.
Report available inventory.
Reserve stock for orders.
Release reservations when orders fail or are cancelled.
Preserve existing inventory behavior.

Requirements:

Inventory MUST own stock persistence.
Inventory MUST prevent negative available quantities.
Concurrent reservations MUST NOT oversell stock.
Reservations and releases MUST be idempotent where retries are possible.
Database locking or optimistic concurrency control MUST be used where necessary.
Other modules MUST NOT directly access inventory repositories or persistence entities.
6.3 Orders module

Responsibilities:

Create and retrieve orders.
Validate order requests.
Coordinate Catalog, Inventory, and Payments.
Maintain order items and order lifecycle transitions.
Handle cancellation and failure scenarios.
Preserve existing order behavior.

Requirements:

Orders MUST own order persistence.
Order items MUST preserve the agreed price snapshot at the time of ordering.
Invalid quantities and invalid order requests MUST be rejected.
Invalid order state transitions MUST be rejected.
Duplicate requests MUST NOT create duplicate orders when an idempotency key is available.
Orders MUST NOT directly access another module's repositories or persistence entities.
Order status transitions MUST be explicit and tested.

Preserve existing order statuses where they are already part of the application contract. Introduce additional statuses only when required by the actual workflow.

6.4 Payments module

Responsibilities:

Maintain existing customer account and payment-related state.
Validate customer eligibility.
Authorize or reserve funds according to the existing business model.
Capture, release, or refund funds where the existing implementation supports these operations.
Prevent duplicate financial effects during retries.

Requirements:

Payments MUST own customer and payment persistence.
Financial calculations MUST use precise monetary representations.
Financial state transitions MUST be auditable.
Authorization, capture, release, and refund MUST remain distinct operations where supported.
Duplicate commands MUST NOT result in duplicate debits, credits, or reservations.
Existing payment behavior MUST be preserved unless a change is explicitly approved.
7. Module API Requirements

Each module MUST expose a small public API consisting of the operations and immutable data types required by other modules.

Examples include:

CatalogFacade: product lookup and order-time product validation.
InventoryFacade: stock checks, reservation, and release.
OrdersFacade: order creation, lookup, and supported lifecycle operations.
PaymentsFacade: customer eligibility and payment authorization or reservation.

These interfaces are illustrative. The implementation MUST follow the actual domain model.

Cross-module communication MUST use one of the following:

A public module API.
A published application event.
An explicitly documented query interface.

The implementation MUST NOT inject another module's repositories or manipulate another module's persistence entities.

Public module APIs SHOULD use immutable DTOs or domain-specific command and result types.

8. Dependency Rules

The initial permitted business dependency graph is:

orders -> catalog
orders -> inventory
orders -> payments


Catalog, Inventory, and Payments MUST NOT depend on Orders.

Additional dependencies MUST have a documented business justification.

The application MUST NOT contain cyclic module dependencies.

Shared infrastructure packages MUST remain small and MUST NOT contain business logic that belongs to a particular module.

9. Spring Modulith Requirements

Use a Spring Modulith version compatible with the repository's actual Spring Boot version.

Requirements:

Configure module discovery using supported Spring Modulith conventions.
Define module boundaries using top-level business packages.
Add automated architecture verification.
Run architecture verification as part of the Maven test lifecycle.
Fail the build when prohibited module dependencies or cycles are detected.
Prevent access to module internals from other modules.
Document any intentional module dependencies.

The architecture test MUST include verification equivalent to:

ApplicationModules.of(RetailApplication.class).verify();


Adapt the import and test configuration to the selected Spring Modulith version.

Do not disable architecture verification to work around package structure problems.

10. Communication Strategy
10.1 Synchronous communication

Internal REST clients between migrated business modules SHOULD be replaced with direct calls to public module APIs when the caller requires an immediate result.

Examples include:

Validating product existence.
Retrieving product information.
Checking inventory.
Reserving inventory.
Authorizing payment.

Synchronous operations MUST preserve the required error-handling and transaction semantics.

10.2 Application events

Spring application events SHOULD be used for notifications and follow-up processing that do not require an immediate response.

Candidate events include:

OrderPlaced
OrderConfirmed
OrderCancelled
InventoryReserved
InventoryReservationFailed
PaymentAuthorized
PaymentAuthorizationFailed

Only implement events that correspond to actual business transitions.

Events MUST:

Be immutable.
Avoid exposing persistence entities.
Include sufficient identifiers for processing.
Be safe to process more than once.
Have documented transaction and failure semantics.
10.3 Event reliability

An after-commit listener alone MUST NOT be treated as durable event delivery.

When business correctness depends on reliable asynchronous processing, use Spring Modulith's supported event publication registry or another documented durable delivery mechanism.

Requirements:

Failed publications MUST be observable.
Failed processing MUST be recoverable.
Event handlers MUST be idempotent.
Duplicate delivery MUST NOT duplicate business effects.
The application MUST document retry behavior.
The application MUST distinguish event publication from successful event handling.
10.4 Kafka

Inventory all existing Kafka topics, producers, and consumers before changing messaging behavior.

For each topic, document:

Producer.
Consumer.
Business purpose.
Ordering requirements.
Retry behavior.
Duplicate-delivery behavior.
Whether consumers exist outside the current application.
Whether the topic remains necessary after migration.

Internal-only communication SHOULD move to Spring application events where appropriate.

Kafka MUST be retained when external integrations, independent consumers, or cross-process delivery require it.

Do not remove Kafka dependencies until all required messaging behavior has been accounted for.

11. Order Processing Workflow

The order workflow MUST preserve the existing business semantics and define the behavior of each operation.

The target workflow MUST support:

Receiving and validating an order request.
Validating customer eligibility.
Retrieving product information and order-time prices.
Validating requested quantities.
Reserving inventory.
Authorizing or reserving payment according to the existing payment model.
Persisting the order and its items.
Returning the appropriate result to the caller.
Compensating for previously completed operations when later operations fail.
Preventing duplicate processing after retries.

The exact sequence MUST be determined by inspecting the existing implementation and documented in an architecture decision record.

The implementation MUST define expected behavior for:

A nonexistent product.
An ineligible customer.
Insufficient inventory.
Failed payment authorization.
Order persistence failure.
Client retries after timeouts.
Event handler failures.
Inventory reserved before order failure.
Funds reserved before order failure.
Order cancellation after reservation or authorization.

The design MUST NOT assume that multiple local transactions provide one atomic transaction across all modules.

When inventory and payment reservations are committed independently, implement explicit compensation, retry, and recovery behavior.

12. Persistence and Transactions
12.1 Persistence ownership

Each business module MUST own its persistence logic.

A module MUST NOT directly access another module's repositories or persistence entities.

12.2 Technology assessment

Before consolidating persistence, inspect:

Datasource configurations.
JPA, JDBC, R2DBC, or jOOQ usage.
Transaction managers.
Database schemas.
Liquibase changelogs.
Existing inter-service database dependencies.
Foreign keys and cross-schema relationships.

Do not assume that different persistence technologies share the same transaction manager or transaction context.

Prefer a consistent persistence strategy where practical, but do not perform a major persistence rewrite without documenting its risks and benefits.

12.3 Database migration

Liquibase MUST remain the schema migration mechanism unless a change is explicitly approved.

Requirements:

Preserve existing changelogs.
Do not destructively modify previously applied changesets.
Introduce new changesets for schema changes.
Preserve business data.
Preserve identifiers where required
