---
name: spec-driven-development
description: Use when implementing, planning, reviewing, or verifying work governed by repository specifications, acceptance criteria, and migration phases.
---

# Spec-Driven Development Skill
1. Purpose

Implement software by treating version-controlled specifications as the primary definition of intended behavior and completion criteria.

The workflow MUST be portable across AI coding assistants. It MUST NOT require provider-specific tools, APIs, commands, or agent features.

2. Required Context

Before starting, read:

AGENTS.md, if present.
spec/spring-modulith-migration.md, when working on the Spring Modulith migration.
The specification for the requested phase under spec/.
Relevant architecture decision records.
Relevant source code, tests, build configuration, and database migrations.

For the Spring Modulith migration, the phase specifications are:

spec/01-modulith-foundation.md
spec/02-catalog-module.md
spec/03-inventory-module.md
spec/04-payments-module.md
spec/05-orders-orchestration.md
spec/06-api-and-deployment.md
spec/07-migration-verification.md

Read only the additional specifications needed to understand the active task and its prerequisites.

Do not assume that illustrative code, package names, or proposed APIs in a specification already exist. Inspect the repository before using them.

3. Identify the Active Task

Determine:

The requested outcome.
The applicable master specification.
The active phase.
The phase prerequisites.
The acceptance criteria.
The affected modules and files.
The required tests.
The expected compatibility constraints.
The operations that require explicit approval.

If the task is ambiguous, resolve it using the existing specifications and implementation where possible.

Ask a focused clarification question when an unresolved ambiguity could cause a significant architectural, business, security, data, or compatibility change.

Do not block straightforward work with unnecessary questions.

4. Establish the Baseline

Before modifying files:

Inspect the working tree and preserve unrelated user changes.
Inspect the relevant source code and existing tests.
Inspect build and dependency configuration.
Inspect persistence schemas and Liquibase changesets where relevant.
Identify existing behavior and external contracts.
Run relevant baseline tests when practical.
Record pre-existing failures.

Do not assume a failing baseline test was caused by the current implementation.

Do not overwrite unrelated work or discard existing changes.

5. Build an Acceptance Criteria Checklist

Translate the active specification into a checklist.

Each criterion MUST be testable or have a clear form of verifiable evidence.

For example:

The module exposes a documented public API.
Other modules cannot access its internal repositories.
The expected failure behavior is tested.
The architecture verification test passes.
The relevant REST API contract is preserved.

Identify the implementation and verification work needed for every applicable criterion.

Do not omit difficult requirements merely because they are inconvenient.

If a criterion cannot be verified automatically, define the manual evidence required.

6. Plan the Smallest Safe Change

Before implementation:

Identify the smallest change that satisfies the requirements.
Identify affected modules and their owners.
Check permitted module dependencies.
Identify persistence and transaction boundaries.
Identify failure, retry, and recovery behavior.
Identify compatibility risks.
Determine which tests need to be added or updated.
Identify any required architecture decision.

Avoid unrelated refactoring and unnecessary framework upgrades.

For changes that affect multiple modules, document the interaction contract before implementing the integration.

7. Implement Using Test-Driven Development

Where practical, follow this cycle:

Write a test that expresses the required behavior.
Run the test and confirm that it fails for the expected reason.
Implement the smallest change that satisfies the test.
Run the test and confirm that it passes.
Refactor without changing behavior.
Rerun the relevant tests.

Do not manufacture a failing test when a requirement is already correctly implemented. Inspect the existing tests and add coverage for missing cases.

Use the existing testing conventions and libraries.

Do not change expected behavior merely to make a test pass unless the specification explicitly requires the change.

8. Respect Architecture Boundaries

For Spring Modulith work:

Keep business logic in its owning module.
Expose only the necessary public module API.
Keep implementation packages internal.
Do not expose persistence entities or repositories across module boundaries.
Avoid cyclic dependencies.
Use synchronous module API calls when an immediate result is required.
Use application events for appropriate notifications and asynchronous work.
Define transaction boundaries and failure recovery explicitly.
Run architecture verification after relevant changes.

The intended business modules are Catalog, Inventory, Payments, and Orders.

The intended dependency direction permits Orders to depend on the public APIs of Catalog, Inventory, and Payments. The other modules must not depend on Orders' internal implementation.

Follow the master specification and approved architecture decisions if the target architecture changes.

9. Handle Requirements and Design Conflicts

When the specification and implementation disagree:

Identify the conflicting requirement and current behavior.
Determine whether the difference is intentional and approved.
Assess the effect on existing consumers, data, and business rules.
Prefer a backward-compatible solution when it satisfies the approved target.
Document the proposed change when a specification update is required.
Ask for approval before consequential or irreversible changes.

Do not silently redefine acceptance criteria.

Do not invent missing business rules.

Do not weaken security or data integrity to simplify the implementation.

10. Verify Each Change

After implementation:

Review the changed files and final diff.
Run targeted unit tests.
Run relevant integration tests.
Run Spring Modulith architecture verification where applicable.
Run API contract or end-to-end tests where required.
Run the broader Maven verification command when appropriate.
Inspect the results and investigate failures.
Map the results to the acceptance criteria.

For Maven projects, use the repository's Maven wrapper where available.

The default full verification command is:

./mvnw clean verify

Adapt this command when the repository uses a different module layout or build configuration.

Do not claim tests passed if they were not executed successfully.

Do not treat compilation alone as proof of business correctness.

Do not treat the creation of a test file as proof that the test passes.

11. Report Evidence, Not Assumptions

For each acceptance criterion, use one of these statuses:

PASS: Verified with supporting evidence.
FAIL: Verification was performed and failed.
BLOCKED: A prerequisite or environmental limitation prevented verification.
NOT VERIFIED: Verification has not been performed or evidence is insufficient.

Record:

The criterion.
The implementation location.
The test or verification command.
The actual result.
Any remaining limitation.

Do not mark untested behavior as passing based on code inspection alone.

If a required external integration cannot be exercised, report the limitation explicitly.

12. Migration Phase Discipline

For the Spring Modulith migration:

Complete the foundation phase before business migration.
Migrate Catalog before dependent business integration requires it.
Migrate Inventory and Payments according to their documented prerequisites.
Implement Orders orchestration after the required public module APIs are available.
Verify API compatibility and deployment behavior.
Perform final migration verification before recommending cutover.

Follow the dependencies in the master and phase specifications.

Do not implement all phases in one task unless explicitly requested and the dependencies can be safely satisfied.

Do not delete original services or perform production cutover as part of routine implementation.

13. Definition of Done

A task is complete only when:

Applicable acceptance criteria have been addressed.
Required implementation changes are present.
Required tests have been executed.
Architecture constraints have been checked where applicable.
Compatibility implications are documented.
The final diff has been reviewed.
Remaining failures and risks are reported.
The completion report reflects actual verification results.

A phase is complete only when its exit criteria are satisfied.

The entire migration is complete only when the final migration specification's requirements have been verified and the necessary approvals have been obtained.

14. Required Completion Report

Use this structure in the final response:

Summary

Explain what was implemented.

Specification

Identify the master specification and active phase.

Changes

List the significant files created, modified, or deleted.

Acceptance criteria

Report the status and evidence for each relevant criterion.

Tests and verification

List the commands executed and their actual outcomes.

Risks and outstanding issues

Identify failures, blocked checks, unverified requirements, and follow-up work.

Next step

Identify the next permitted action or migration phase.

15. Portability Requirements

Keep this skill compatible with different AI coding assistants.

Use standard Markdown and YAML frontmatter.
Do not require a specific AI provider.
Do not assume provider-specific tools are available.
Use repository files and test commands as the source of verifiable evidence.
Keep business requirements in spec/.
Keep repository-wide engineering requirements in AGENTS.md.
Keep reusable development workflows in skills.
Keep provider-specific configuration in separate adapter files.

The skill MUST describe the required outcome and verification process rather than relying on a specific agent's implementation mechanism.
