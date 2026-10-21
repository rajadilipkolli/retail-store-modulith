Repository AI Agent Instructions
1. Purpose

This repository contains a Spring Boot microservices application that is being evolved toward a Spring Modulith architecture through specification-driven development (SDD).

All AI coding agents MUST follow these instructions when analyzing, implementing, testing, reviewing, or documenting changes in this repository.

These instructions are tool-neutral and apply regardless of the AI coding assistant being used.

2. Source of Truth

Use the following precedence when resolving requirements:

Explicit user instructions for the current task.
Applicable repository security policies and mandatory engineering constraints.
spec/spring-modulith-migration.md, the master migration specification.
The active phase specification under spec/.
Relevant architecture decision records and approved design decisions.
Existing implementation and tests, which provide evidence of current behavior.
Shared engineering instructions and skills.

If two requirements conflict, do not silently choose one. Explain the conflict and request clarification when the correct resolution cannot be determined safely.

Existing implementation details MUST NOT override an explicitly approved target requirement, but existing business behavior MUST be preserved unless a change is authorized.

3. Required Reading Before Changes

Before implementing a task, the agent MUST:

Read this file.
Read spec/spring-modulith-migration.md, if the task relates to the migration.
Read the specification for the active migration phase.
Read relevant skills from ai/skills/.
Inspect the affected source code, tests, build configuration, and database migrations.
Identify the acceptance criteria relevant to the task.
Check the working tree and avoid overwriting unrelated user changes.

Do not assume that file names, package names, dependencies, endpoints, or business rules mentioned in a specification already exist. Verify them against the repository.

If a required specification is missing, stop the affected implementation and report the missing prerequisite.

4. Specification-Driven Development

Specifications define intended behavior and acceptance criteria.

For every implementation task:

Identify the applicable specification.
Translate its acceptance criteria into concrete implementation and test tasks.
Inspect the existing implementation and identify gaps.
Design the smallest appropriate change.
Write or update tests before implementing behavior changes where practical.
Implement the change.
Run targeted tests.
Run the relevant architecture checks.
Run the broader build and test suite when appropriate.
Report the results and any remaining gaps.

Do not implement the entire migration when asked to complete a single phase.

Do not mark a specification complete merely because source files have been created.

If implementation reveals a requirement that is infeasible, ambiguous, unsafe, or incompatible with the existing architecture, document the issue and propose a resolution before making a consequential design change.

5. Migration Phase Discipline

The Spring Modulith migration is divided into these phases:

Phase 1: Foundation and architecture verification.
Phase 2: Catalog module.
Phase 3: Inventory module.
Phase 4: Payments module.
Phase 5: Orders orchestration.
Phase 6: API compatibility and deployment.
Phase 7: Migration verification.

Follow the dependencies defined by the phase specifications.

Do not begin a dependent phase until its prerequisites are satisfied, unless the user explicitly authorizes parallel work and the dependencies can be managed safely.

Do not retire existing microservices, delete deployment assets, or remove production integrations before migration verification and explicit approval.

6. Technology and Dependency Rules

Before changing technology versions:

Inspect the existing Java version.
Inspect the Spring Boot version.
Inspect Maven dependency management.
Verify Spring Modulith compatibility with the existing Spring Boot version.
Reuse existing libraries when they satisfy the requirement.
Avoid unnecessary dependency upgrades.
Explain any required compatibility changes.

Use the repository's established conventions unless the specification requires a justified change.

Do not invent dependency versions, APIs, environment variables, or configuration properties.

Prefer the repository's Maven wrapper when available.

7. Spring Modulith Architecture Rules

The target business modules are:

catalog
inventory
payments
orders

Adapt package names to the actual application root package.

Each module MUST have clearly documented ownership and boundaries.

The intended dependency direction is:

Orders may depend on Catalog's public API.
Orders may depend on Inventory's public API.
Orders may depend on Payments' public API.
Catalog, Inventory, and Payments MUST NOT depend on Orders' internal implementation.
Business modules MUST NOT introduce cyclic dependencies.
No module may access another module's internal persistence implementation.

Cross-module communication MUST use documented public module APIs, immutable data-transfer objects, or appropriately designed application events.

Do not expose JPA entities, repository interfaces, mutable persistence objects, or persistence sessions across module boundaries.

Use direct module API calls when the caller needs an immediate result. Use application events for appropriate notifications and asynchronous follow-up work.

Do not introduce events merely to replace every direct method call.

8. Transactions and Business Consistency

Define transaction boundaries explicitly.

Do not assume that one Spring application process makes multiple business operations automatically atomic.

For order processing, evaluate:

Inventory reservation.
Payment authorization or reservation.
Order persistence.
Failure compensation.
Idempotent retries.
Event publication and recovery.

Compensation operations MUST be safe to retry.

For business-critical asynchronous processing, assess durable event publication, transactional event handling, retry policies, and recovery mechanisms.

Do not claim that database rollback reverses an external payment or other external side effect.

9. Persistence and Database Migrations

Preserve existing business data and identifiers unless a migration explicitly requires a change.

Follow these rules:

Use the repository's established persistence technology.
Use Liquibase when it is the established schema migration mechanism.
Add new changesets rather than destructively editing already-applied changesets.
Preserve relevant database constraints and indexes.
Test initialization from an empty database.
Test upgrades against representative existing schemas and data.
Document destructive or irreversible operations.
Never reset production data as part of ordinary development or verification.

Do not access another module's repositories or manipulate its persistence entities directly.

10. Testing and Quality

Tests are required evidence of implementation correctness.

Use the existing testing stack and conventions.

Depending on the change, add or update:

Unit tests.
Application and persistence integration tests.
REST API contract tests.
Spring Modulith architecture tests.
Concurrency and idempotency tests.
Failure and recovery tests.
End-to-end tests.

Use PostgreSQL integration testing where database-specific behavior matters.

Architecture verification MUST run through the appropriate automated build lifecycle.

Do not weaken tests, disable architecture checks, or suppress failures merely to obtain a passing build.

Do not claim a test passed unless it was actually executed successfully.

Distinguish pre-existing failures from regressions introduced by the current change.

11. API and Compatibility

Preserve existing externally consumed API contracts unless a change is explicitly approved.

Check relevant:

Routes and HTTP methods.
Request and response structures.
Validation rules.
Status codes and error formats.
Authentication and authorization.
Pagination and filtering.
Frontend integration.
Messaging contracts and external integrations.

Keep external transport DTOs separate from internal persistence models where appropriate.

Document intentional compatibility changes and their impact on consumers.

12. Security

Never commit secrets, credentials, tokens, private keys, or production configuration containing sensitive values.

Do not expose sensitive information through logs, API responses, exceptions, or application events.

Preserve existing security behavior unless a change is explicitly required and reviewed.

Validate input at appropriate boundaries and follow established authorization rules.

Do not execute destructive production operations without explicit authorization.

13. Implementation Practices

Prefer small, focused, reviewable changes.

Follow existing code style and naming conventions.

Prefer clear, maintainable implementations over unnecessary abstractions.

Avoid unrelated refactoring, broad dependency upgrades, and changes outside the active task.

Do not silently replace an existing implementation with a generic example.

If a specification conflicts with verified business behavior, identify the conflict and propose an explicit resolution.

Update documentation and architecture decision records when an approved design decision changes.

14. Build and Verification

Before finishing a task:

Review the changed files.
Run targeted tests.
Run applicable architecture verification.
Run the appropriate broader Maven verification command.
Inspect the final diff for unrelated changes and accidental secrets.
Map the implementation to the relevant acceptance criteria.

Use the Maven wrapper when available. For a Maven project, the default full verification command is:

./mvnw clean verify

Adapt the command to the repository's actual structure and build requirements.

If a command cannot run because of an environmental limitation, state that clearly.

Do not report successful verification when tests were skipped, blocked, or not executed.

15. Required Completion Report

At the end of each implementation task, provide:

Summary

Describe what was implemented and why.

Changed files

List the important files created, modified, or deleted.

Specification coverage

Map the applicable acceptance criteria to their implementation and evidence.

Verification

List the commands executed and their actual outcomes.

Outstanding issues

Identify failures, unverified criteria, risks, and required follow-up.

Next step

Identify the next permitted phase or task, if applicable.

Use these statuses when reporting acceptance criteria:

PASS: Verified with evidence.
FAIL: Verification failed.
BLOCKED: Cannot be completed because of a prerequisite or environmental issue.
NOT VERIFIED: Evidence is insufficient or verification has not been performed.

Never claim the complete migration is finished while required acceptance criteria remain unverified.

16. Shared Skills

Before a task, consult relevant skills under ai/skills/.

The initial shared skill is:

ai/skills/spec-driven-development/SKILL.md

Additional skills may cover Spring Modulith, test-driven development, database migrations, security, and migration verification.

Skills provide reusable implementation workflows. They do not override the master specification, approved architecture decisions, or explicit user requirements.

17. Tool Independence

These instructions MUST remain usable across different AI coding assistants.

Do not assume the availability of a specific provider's tools, commands, plugins, agent modes, or context-management features.

Use ordinary repository files, Markdown, build commands, and automated tests as the portable foundation.

If a provider-specific feature is needed, document it as an optional integration rather than a mandatory project requirement.
