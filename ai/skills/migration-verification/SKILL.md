---
name: migration-verification
description: Use when validating migration phases, checking specification acceptance criteria, assessing compatibility, verifying data integrity, or preparing a Spring Modulith migration for approval.
---
#Migration Verification Skill
1. Purpose

Verify migration readiness through executable tests, architecture checks, compatibility evidence, and explicit acceptance criteria.

This skill MUST distinguish implemented functionality from functionality that has actually been verified.

2. Required Context

Read:

AGENTS.md.
spec/spring-modulith-migration.md.
The active phase specification.
spec/execution-status.md, if present.
Relevant architecture decision records.
Relevant implementation, tests, database migrations, and deployment configuration.

Do not assume a phase is complete because its status file says COMPLETE.

3. Acceptance Criteria Verification

For each criterion, record:

Criterion identifier or description.
Required behavior.
Verification method.
Command or test used.
Actual result.
Evidence location.
Remaining risks.

Use these statuses:

PASS: Verified with supporting evidence.
FAIL: Verification was performed and failed.
BLOCKED: Verification could not proceed because a prerequisite or environment was unavailable.
NOT VERIFIED: Sufficient evidence has not been obtained.

Do not mark criteria PASS based only on code inspection when executable verification is required.

4. Build and Test Verification

Run the appropriate targeted tests, architecture checks, integration tests, and full build.

For Maven, use the repository's wrapper when available.

Default full verification command:

./mvnw clean verify

Record:

Exact command.
Exit code or observed result.
Failed tests.
Skipped tests.
Environmental limitations.
Known pre-existing failures.

Do not hide failures by changing test configuration or suppressing test execution.

5. Architecture Verification

For Spring Modulith, verify:

Expected modules are discovered.
Module boundaries are documented.
Prohibited dependencies are rejected.
Cyclic dependencies are absent.
Internal persistence implementations are not accessed across module boundaries.
Public APIs are used for cross-module calls.
Application event behavior follows documented transaction and reliability requirements.

Confirm that the architecture tests actually run during the build.

6. Business and Data Integrity

Where applicable, verify:

Existing API contracts.
Stable identifiers and required data preservation.
Liquibase initialization and upgrade paths.
Database constraints.
Concurrent inventory reservation.
Payment idempotency and financial invariants.
Duplicate order request handling.
Order cancellation and compensation.
Event retries and duplicate delivery.
Final persisted state after partial failure.

Do not use production data for destructive tests.

Do not claim external side effects are rolled back by local database transactions.

7. Compatibility Verification

Compare the new implementation with the original system.

Inspect:

Routes and HTTP methods.
Request and response formats.
Error behavior.
Authentication and authorization.
Database assumptions.
Kafka topics and external consumers.
Configuration and deployment requirements.
Frontend integration.

Document intentional incompatibilities and require the appropriate approval.

8. Operational Readiness

Before recommending cutover, verify:

Deployment and configuration instructions.
Health and readiness checks.
Database backup and recovery requirements.
Required integrations.
Logging and operational monitoring.
Rollback triggers and procedures.
Data consistency when switching between old and new implementations.

Never perform production cutover, delete production resources, or retire existing microservices without explicit authorization.

9. Phase Completion Rules

A phase may be recommended for completion only when:

All mandatory acceptance criteria are PASS.
Required tests have been executed successfully.
Architecture constraints are verified.
Required compatibility checks are complete.
Outstanding risks are documented.
Required reviewer approval is recorded.

If any mandatory criterion is FAIL, BLOCKED, or NOT VERIFIED, the phase MUST NOT be represented as fully verified.

10. Completion Report

Produce a report containing:

Overall status

PASS, FAIL, BLOCKED, or NOT VERIFIED.

Acceptance criteria

Status and evidence for each requirement.

Build and test results

Commands executed and actual outcomes.

Data and compatibility

Migration, integrity, and API verification results.

Risks

Unresolved issues and their impact.

Recommendation

Proceed, remediate, or request review.

A recommendation is not a production authorization.