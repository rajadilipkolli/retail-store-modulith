---
title: "Specification 01: Spring Modulith Foundation"
status: "Proposed"
depends_on: "spec/spring-modulith-migration.md"
phase: "1 — Modulith Foundation"
---

Specification 01: Spring Modulith Foundation
1. Objective

Establish the technical foundation for the Spring Modulith application without migrating business behavior prematurely.

The result MUST be a buildable Spring Boot application with explicit module boundaries, compatible dependencies, automated architecture verification, and a documented development workflow.

2. Repository Assessment

Before making changes, the implementation agent MUST:

Read the master specification and repository instructions.
Inspect the root Maven POM and relevant module POMs.
Identify the current Java and Spring Boot versions.
Identify existing application entry points and package conventions.
Inspect persistence technologies, transaction managers, Liquibase configuration, security, and observability.
Run the existing build and tests.
Record pre-existing build failures.
Identify conflicts between the current architecture and the target design.

Do not upgrade framework versions or restructure existing services without justification.

3. Application Requirements

Create a new Spring Boot application for the modular monolith.

Requirements:

Use the repository's existing package conventions where practical.
Use Java 25 unless repository inspection establishes an approved alternative.
Use Maven and existing dependency-management conventions.
Select a Spring Modulith release compatible with the existing Spring Boot version.
Avoid unrelated dependency upgrades.
Keep existing microservices buildable.
Do not delete existing business functionality during this phase.

The application MUST have a single documented entry point.

4. Module Structure

Establish the following top-level modules:

catalog
inventory
orders
payments
shared
ui

Use package names appropriate to the existing root package.

Each module MUST have a clear package boundary. Use package-info.java, supported Spring Modulith annotations, or other appropriate conventions to document module intent and allowed dependencies.

The foundation phase MAY establish empty module skeletons. It MUST NOT claim business functionality has been migrated when it has not.

5. Module Dependency Policy

The initial permitted dependency graph is:

ui -> catalog
ui -> inventory
ui -> orders
ui -> payments
ui -> shared
catalog -> shared
inventory -> shared
payments -> shared
orders -> shared


Shared is an OPEN module providing common abstractions, DTOs, and cross-cutting models. Any module may refer to Shared.

Catalog, Inventory, Order and Payments MUST NOT depend on UI.

No module may depend on another module's internal implementation.

Cross-module access MUST eventually occur through public module APIs, documented query interfaces, or published events.

Do not introduce artificial dependencies merely to make module discovery succeed.

6. Architecture Verification

Add automated Spring Modulith architecture tests.

The tests MUST:

Discover the intended business modules.
Verify module dependency rules.
Detect cyclic dependencies.
Reject access to prohibited internal packages.
Run as part of the Maven test lifecycle.

Include verification equivalent to:

ApplicationModules.of(RetailApplication.class).verify();


Adapt the application class, imports, and test setup to the actual implementation and selected Spring Modulith version.

The build MUST fail when a prohibited dependency is introduced.

Do not disable verification to bypass a design problem.

7. Persistence and Configuration Assessment

Before consolidating persistence, document:

Datasource configurations.
JPA, JDBC, R2DBC, or jOOQ usage.
Transaction managers.
Liquibase changelogs.
Database schemas and ownership.
Security configuration.
External service clients.
Kafka producers and consumers.

Do not merge database configurations blindly.

Do not introduce shared database entities or cross-module repository access as part of the foundation.

8. Testing Requirements

Add tests that verify:

The new application context can start.
The intended modules can be discovered.
Architecture verification succeeds.
The Maven test lifecycle executes architecture tests.
Existing services remain buildable.

Use the repository's established testing conventions.

9. Documentation

Document:

How to build the new application.
How to run architecture tests.
The intended module dependency graph.
The selected Spring Modulith version and compatibility rationale.
Outstanding architecture decisions.
Known baseline test failures.

Create architecture decision records using the repository's existing ADR convention or architecture/decisions/.

10. Acceptance Criteria
Existing build and test baselines are recorded.
The target application has one documented entry point.
Business, shared, and UI module boundaries are established.
Spring Modulith dependencies are compatible with the selected Spring Boot version.
Architecture verification runs automatically.
Prohibited module dependencies are detected.
Existing business services remain intact.
No business functionality is falsely reported as migrated.
Documentation and relevant architecture decisions are committed.
11. Exit Criteria

The foundation phase is complete when the new application builds, module discovery and architecture verification work, and the implementation is ready for the Catalog migration.

Run the appropriate Maven verification command and report the actual result. Do not proceed to business migration if the foundation is not stable.

12. Implementation Rules

Implement only this phase.

Do not migrate Catalog, Inventory, Payments, or Orders business logic in this task.

If repository constraints require a change to the master specification, document the reason and proposed adjustment before making a destructive or externally visible change.