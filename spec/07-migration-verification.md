---
title: "Specification 07: Migration Verification and Completion"
status: "Proposed"
depends_on: "spec/01-modulith-foundation.md through spec/06-api-and-deployment.md"
phase: "7 — Final Verification"
---

Specification 07: Migration Verification and Completion
1. Objective

Verify that the Spring Modulith migration satisfies the master specification, preserves required business behavior, and is ready for an approved production cutover.

This phase MUST validate the implementation rather than merely check whether files and classes exist.

The coding agent MUST NOT declare the migration complete without executable test results and evidence for the acceptance criteria.

2. Preconditions

Before verification, confirm that:

The modular monolith builds.
Catalog, Inventory, Orders, and Payments are implemented.
Public module APIs are defined.
Module dependency rules are enforced.
Required database migrations exist.
Required REST endpoints are available.
Required event integrations are documented.
Deployment instructions are available.

If a prerequisite is incomplete, report it as a blocker rather than silently skipping the corresponding verification.

3. Build Verification

Run the complete Maven verification suite:

./mvnw clean verify


Verify that:

Compilation succeeds.
Unit tests pass.
Integration tests pass.
Architecture tests execute.
Test failures are reported accurately.
No required tests are silently skipped.
The final build result is recorded.

If the repository uses a different Maven wrapper path or build configuration, document and use the correct command.

Record the exact command and result.

Pre-existing failures MUST be distinguished from failures introduced by the migration.

4. Architecture Verification

Verify that Spring Modulith discovers the expected business modules:

Catalog.
Inventory.
Orders.
Payments.

Confirm that:

The application has one primary entry point.
Module boundaries are documented.
Architecture verification runs automatically.
Prohibited dependency cycles are rejected.
Other modules cannot access internal implementations.
Repositories and persistence entities remain within their owning modules.
Cross-module communication uses approved public APIs or events.

Verify that the permitted dependency graph remains consistent with the master specification:

orders -> catalog
orders -> inventory
orders -> payments


Any intentional deviation MUST be documented and approved.

5. Business Behavior Verification
5.1 Catalog

Verify:

Existing product identifiers are preserved.
Product lookup behaves correctly.
Validation rules are preserved.
Pricing uses appropriate precise representations.
Existing catalog API contracts remain compatible.
5.2 Inventory

Verify:

Stock quantities remain consistent.
Available stock cannot become negative.
Concurrent reservations cannot oversell stock.
Duplicate reservation requests do not reserve stock twice.
Duplicate releases do not restore stock repeatedly.
Failed order processing follows the documented compensation policy.
5.3 Payments

Verify:

Customer eligibility rules remain correct.
Payment operations preserve financial invariants.
Duplicate requests do not duplicate financial effects.
Invalid payment state transitions are rejected.
Payment failures are handled according to the documented workflow.
Compensation and recovery behavior are tested where applicable.
5.4 Orders

Verify:

Valid orders can be created.
Invalid requests are rejected.
Order-time price snapshots are preserved.
Order state transitions are validated.
Duplicate requests do not create duplicate orders.
Cancellation follows the documented business rules.
Inventory and payment outcomes remain consistent after failures.
6. Failure and Recovery Verification

Execute tests for the following scenarios:

Product lookup failure.
Customer eligibility failure.
Insufficient inventory.
Payment authorization failure.
Order persistence failure.
Concurrent duplicate order requests.
Client timeout followed by a retry.
Failure after inventory reservation.
Failure after payment authorization or reservation.
Failed application event processing.
Repeated processing of the same event.
Compensation operation failure.

For each scenario, document:

Initial state.
Operation performed.
Expected outcome.
Actual outcome.
Final order state.
Final inventory state.
Final payment state.
Recovery action, if required.

Do not claim that a failure scenario is handled correctly merely because an exception is thrown.

Verify the final persisted business state.

7. Event Reliability Verification

Inspect all application events and retained Kafka integrations.

Verify:

Event names and payloads are documented.
Events do not expose mutable persistence entities.
Transaction boundaries are correct.
Required event publication is durable where necessary.
Failed event publications can be detected.
Failed processing can be retried or recovered.
Event handlers are idempotent.
Duplicate delivery does not duplicate business effects.
External Kafka contracts remain compatible where applicable.

An after-commit listener without durable recovery MUST NOT be accepted as sufficient for a business-critical delivery requirement.

Document any remaining limitations.

8. Database Verification

Verify:

Liquibase initializes an empty database successfully.
A representative existing database can be upgraded.
Existing business records are preserved.
Identifiers remain stable where required.
Database constraints remain effective.
Inventory and financial invariants survive failures.
Transaction rollback behaves as expected.
No destructive changes were introduced without approval.

Where feasible, use PostgreSQL Testcontainers for integration testing.

Document the database backup and rollback procedure.

Do not run destructive verification against production data.

9. API and Security Verification

Compare the migrated API behavior with the original implementation.

Verify:

HTTP methods and routes.
Request and response formats.
Validation behavior.
HTTP status codes.
Error response formats.
Authentication and authorization.
Pagination and filtering.
Frontend integration.
Required external integrations.

Document intentional changes and obtain the required approval.

Verify that no credentials, secrets, or sensitive customer data have been exposed through logs or error responses.

10. Deployment Verification

Follow the documented local deployment instructions from a clean environment.

Verify:

Application startup.
Database connectivity.
Liquibase execution.
Health and readiness endpoints.
Required environment configuration.
Frontend access.
Retained external integrations.
Application shutdown.
Restart behavior.
Observability and error reporting.

Confirm that the monolith does not accidentally require retired infrastructure components.

Do not remove the original microservices from deployment before explicit approval.

11. Regression and Performance Verification

Run the available regression tests.

Where baseline performance data exists, compare:

Order creation latency.
Product lookup latency.
Inventory reservation latency.
Payment operation latency.
Database connection usage.
Error rates.
Resource consumption.

Use representative workloads and document the test environment.

Do not invent performance thresholds or claim improvements without measured evidence.

If a critical regression is identified, document its impact and proposed remediation.

12. Migration and Rollback Readiness

Document the cutover plan.

It MUST include:

Deployment prerequisites.
Database backup requirements.
Configuration changes.
Frontend or gateway routing changes.
Smoke tests.
Monitoring requirements.
Rollback triggers.
Rollback steps.
Data consistency considerations.
Ownership and approval requirements.

If the original services and the monolith can both write to the same business data, document how conflicting writes will be prevented during cutover.

Do not assume that application rollback automatically reverses database changes or external financial operations.

Do not perform production cutover or delete existing production infrastructure without explicit authorization.

13. Acceptance Criteria
The complete Maven verification suite passes with no required tests skipped; FAIL, BLOCKED, or NOT VERIFIED results block completion under the Section 14 status rule.
Every acceptance criterion is PASS with supporting evidence; any non-PASS criterion blocks completion under the same rule.
Spring Modulith architecture verification passes.
All four business modules have verified boundaries.
Business dependency rules are enforced.
Catalog behavior is verified.
Inventory concurrency and reservation behavior are verified.
Payment idempotency and financial invariants are verified.
Order processing and compensation are verified.
Event reliability requirements are tested.
Liquibase initialization and upgrade tests pass.
Existing API contracts are verified or intentional changes approved.
Security behavior is verified.
Deployment instructions have been followed successfully.
Required integrations remain functional.
Rollback procedures are documented.
Remaining risks and limitations are documented.
No production services or data were removed without approval.
14. Required Final Report

Produce a final migration report containing:

A. Build and test results
Commands executed.
Build results.
Unit test results.
Integration test results.
Architecture verification results.
End-to-end test results.
Known failures and skipped tests.
B. Architecture results
Implemented modules.
Public module APIs.
Dependency verification.
Retained external integrations.
Removed obsolete dependencies.
C. Data and compatibility results
Database migration results.
Data integrity verification.
API compatibility results.
Security verification.
D. Operational readiness
Startup and deployment results.
Health and observability results.
Rollback readiness.
Outstanding production risks.
E. Acceptance criteria matrix

For every acceptance criterion in spec/spring-modulith-migration.md and the phase specifications, report:

Criterion.
Status: PASS, FAIL, BLOCKED, or NOT VERIFIED.
Evidence.
Relevant test or implementation reference.
Remediation required, if applicable.

Do not mark a criterion PASS without supporting evidence.

Completion status rule: every FAIL, BLOCKED, or NOT VERIFIED acceptance criterion MUST block completion, regardless of severity. No completion exceptions are permitted. Documenting a failure, skipped test, limitation, or approval request does not waive this rule; retain the actual status until remediation and passing verification.

15. Definition of Done

The migration is ready for approval only when:

Every acceptance criterion is PASS with evidence, following the Section 14 completion status rule.
Required tests pass.
Architecture constraints are enforced.
Business behavior and data integrity are preserved.
Required external integrations function correctly.
Deployment and rollback procedures are documented.
Outstanding risks are disclosed.
The final report is complete.

Every non-PASS acceptance criterion MUST block the claim that the migration is complete, following the Section 14 completion status rule.

Production cutover and retirement of the original microservices require explicit approval.

16. Implementation Rules

This phase is primarily verification and remediation.

Do not introduce unrelated architectural changes during final verification.

When a failure is found:

Reproduce it.
Identify the root cause.
Add or update a regression test.
Implement the smallest appropriate fix.
Rerun targeted tests.
Rerun the complete verification suite when appropriate.
Update the evidence and final report.

Never weaken tests or acceptance criteria merely to obtain a passing build.
