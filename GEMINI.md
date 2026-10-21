Gemini CLI Instructions
1. Purpose

This repository uses specification-driven development (SDD) to migrate a Spring Boot microservices application toward Spring Modulith.

These instructions guide Gemini CLI when planning, implementing, testing, reviewing, or documenting changes.

2. Required Instructions

Before making repository changes:

Read AGENTS.md and follow its repository-wide engineering rules.
Read spec/spring-modulith-migration.md when working on the Spring Modulith migration.
Read the applicable phase specification under spec/.
Read ai/skills/spec-driven-development/SKILL.md for specification-driven tasks.
Read ai/skills/spring-modulith/SKILL.md for Spring Modulith architecture changes.
Inspect the affected source code, tests, Maven configuration, and database migrations.

If a referenced file does not exist, report the missing prerequisite instead of pretending to have read it.

3. Source of Truth

Use the following guidance when interpreting repository requirements:

Explicit user instructions define the current task.
AGENTS.md defines shared repository engineering rules.
spec/spring-modulith-migration.md defines the master migration requirements.
Numbered specifications under spec/ define phase-specific requirements and acceptance criteria.
Approved architecture decision records document architectural choices.
Existing source code and tests provide evidence of current behavior.

If requirements conflict, explain the conflict and request clarification when it cannot be resolved safely.

Do not silently weaken acceptance criteria or invent business rules.

4. Specification-Driven Workflow

For each task:

Identify the active specification and phase.
Verify prerequisites and acceptance criteria.
Inspect the current implementation and record relevant baseline failures.
Plan the smallest appropriate change.
Add or update tests before changing behavior where practical.
Implement only the requested scope.
Run targeted tests and applicable architecture checks.
Run the broader Maven verification suite when appropriate.
Map the implementation to acceptance criteria.
Report actual results and remaining issues.

Do not mark a criterion as passing unless there is supporting evidence.

Do not start a dependent migration phase before its prerequisites are satisfied unless the user explicitly authorizes an appropriate alternative.

5. Spring Modulith Architecture

The target business modules are:

catalog
inventory
payments
orders

Adapt package names to the repository's actual root package.

Enforce these principles:

Each module owns its domain logic and persistence.
Other modules access functionality through documented public module APIs.
Do not expose repositories, JPA entities, or internal implementation packages across module boundaries.
Avoid cyclic module dependencies.
Orders may depend on the public APIs of Catalog, Inventory, and Payments.
Catalog, Inventory, and Payments MUST NOT depend on Orders, including its public APIs and events.
Other modules MUST NOT access Orders' internal implementation.
Use synchronous module APIs when an immediate result is required.
Use application events for appropriate notifications and asynchronous work.
Define transaction boundaries, retries, idempotency, and compensation explicitly.

Run the repository's Spring Modulith architecture verification after relevant changes.

6. Implementation Constraints
Preserve existing business behavior unless a change is approved.
Avoid unrelated refactoring and unnecessary dependency upgrades.
Verify Spring Boot and Spring Modulith compatibility before changing versions.
Preserve existing data and identifiers wherever required.
Follow existing Liquibase conventions.
Do not destructively modify applied database changesets.
Never commit credentials, secrets, or sensitive production configuration.
Do not remove original microservices or perform production cutover without explicit approval.
Do not weaken tests to obtain a passing build.
7. Build and Testing

Inspect the repository before selecting build commands.

For a Maven project, prefer the Maven wrapper when available.

The default full verification command is:

./mvnw clean verify

Use targeted tests during development and the complete verification suite when appropriate.

Report whether tests passed, failed, were blocked, or were not executed.

Never claim that a command succeeded unless it was actually executed and its result was observed.

8. Automated Specification Validation

When modifying specifications, skills, or their references, run:

python3 tools/verify-specs.py

If the script is not present, report that prerequisite and do not claim automated specification validation succeeded.

Fix validation errors introduced by the change. Distinguish existing repository issues from newly introduced problems.

9. Scope Control

Implement only the requested task or migration phase.

Before making a consequential architecture change, explain the problem, its impact, and the proposed resolution.

Do not assume that a single Spring application process makes independently committed business operations atomic.

Do not claim an external payment can be reversed by rolling back a local database transaction.

Preserve external API contracts unless changes are approved and documented.

10. Completion Report

When finishing a task, report:

Summary

Describe the implementation.

Changed files

List important files created, modified, or deleted.

Specification coverage

Map relevant acceptance criteria to implementation evidence.

Verification

List commands executed and their actual outcomes.

Outstanding issues

Report failures, blocked checks, unverified criteria, and risks.

Next step

Identify the next permitted task or migration phase.

Use these statuses for acceptance criteria:

PASS: Verified with evidence.
FAIL: Verification was performed and failed.
BLOCKED: A prerequisite or environmental limitation prevented verification.
NOT VERIFIED: Evidence is insufficient or verification was not performed.
11. Tool Independence

These repository requirements are tool-neutral.

Do not assume that provider-specific tools, plugins, agent modes, or commands are available.

Use version-controlled specifications, shared skills, source code, and executable tests as the common foundation.

When Gemini-specific behavior is needed, follow Gemini CLI's supported configuration and skill-discovery mechanisms without duplicating the repository's engineering rules.