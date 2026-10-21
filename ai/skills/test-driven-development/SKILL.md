---
name: test-driven-development
description: Use when implementing new behavior, fixing defects, refactoring code, or adding regression coverage in this repository.
---
#Test-Driven Development Skill
1. Purpose

Implement reliable changes through small, testable steps. Tests must verify observable behavior and protect existing business invariants.

This skill is tool-neutral and must work with any AI coding assistant.

2. Required Context

Before implementation:

Read AGENTS.md.
Read the relevant master and phase specifications.
Read ai/skills/spec-driven-development/SKILL.md.
Inspect existing source code and tests.
Identify relevant acceptance criteria and existing conventions.

Do not invent APIs, dependencies, business rules, or expected outcomes.

3. Red-Green-Refactor Workflow

For each behavior change:

Red: Write a failing test
Express one requirement through a focused test.
Prefer observable behavior over implementation details.
Include meaningful assertions.
Run the test and confirm it fails for the expected reason.

A test that fails because of a compilation error or broken test environment does not establish the intended behavior is missing.

Green: Implement the minimum change
Implement the smallest change that satisfies the test.
Preserve unrelated behavior.
Avoid introducing unnecessary abstractions.
Rerun the test and confirm it passes.
Refactor: Improve safely
Simplify duplication and improve naming where appropriate.
Preserve the behavior established by the tests.
Rerun the affected tests after refactoring.

If the behavior is already implemented correctly, extend existing coverage rather than manufacturing a failure.

4. Test Selection

Use the repository's existing testing stack.

Choose test types based on the requirement:

Unit tests for isolated business rules.
Integration tests for Spring configuration, persistence, transactions, and module APIs.
Architecture tests for module boundaries and dependencies.
API tests for HTTP contracts and validation.
End-to-end tests for critical cross-module workflows.
Concurrency tests for competing updates and duplicate requests.

Avoid using end-to-end tests for every small rule when a focused unit test provides sufficient evidence.

5. Spring Modulith Testing Rules

For changes involving business modules:

Test public module APIs instead of reaching into another module's internals.
Verify module boundaries through Spring Modulith architecture tests.
Keep persistence integration tests close to their owning module.
Test event publication and listener behavior where events are used.
Verify transaction behavior at the actual persistence boundary.
Test failure handling and retry behavior where business consistency depends on it.

Do not use mocks as the only evidence for database constraints, transaction rollback, or concurrency correctness.

Use PostgreSQL-based integration testing when behavior depends on PostgreSQL-specific semantics.

6. Required Failure Scenarios

Where applicable, test:

Invalid inputs.
Missing records.
Duplicate requests.
Concurrent operations.
Persistence failures.
Transaction rollback.
Event listener failures.
Retry behavior.
Compensation failures.
Unauthorized operations.

For order workflows, assert final persisted order, inventory, and payment states. An exception or HTTP error alone does not prove business consistency.

7. Test Quality Rules
Tests MUST be deterministic and repeatable.
Avoid unnecessary sleeps and timing assumptions.
Clean up test data safely.
Use explicit assertions.
Keep tests independent where practical.
Do not depend on production credentials or services.
Do not weaken assertions merely to make tests pass.
Do not disable failing tests without an approved reason.
Do not change business expectations solely to match an incorrect implementation.
8. Verification

Run the focused test first, then the relevant broader test suite.

For Maven, use the repository's wrapper when available.

Default full verification command:

./mvnw clean verify

Adapt the command to the repository structure and available build profiles.

Record the exact commands and actual outcomes.

If a test cannot run because of environmental limitations, report it as blocked or not verified.

9. Definition of Done

A behavior change is complete when:

The applicable requirement is identified.
Appropriate tests cover the behavior.
Tests pass, or unresolved failures are explicitly reported.
Regression coverage exists for defect fixes.
Architecture constraints remain satisfied.
Relevant acceptance criteria are mapped to evidence.
The final diff contains no unrelated changes.
10. Completion Report

Report:

Behavior implemented or corrected.
Tests added or changed.
Commands executed and their actual outcomes.
Acceptance criteria verified.
Outstanding failures and limitations.