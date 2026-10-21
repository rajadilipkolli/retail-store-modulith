# Retail Store Modulith

A specification-driven migration of the [Spring Boot Microservices Series](https://github.com/rajadilipkolli/spring-boot-microservices-series-v2
) toward a Spring Modulith architecture.

1. Project Goal

The goal is to evolve the existing retail microservices application into a modular monolith while preserving business behavior, API compatibility, security, and data integrity.

The target business modules are:

    Catalog — product information and catalog operations.
    Inventory — stock availability, reservations, and releases.
    Payments — payment and customer financial state.
    Orders — order lifecycle and business workflow orchestration.

The migration is incremental and test-driven. Existing microservices must remain intact until the replacement has been verified and the appropriate retirement approval has been granted.

2. Specification-Driven Development

This repository uses specification-driven development (SDD).

Specifications define intended behavior and acceptance criteria. Implementation is considered complete only when the applicable criteria have supporting verification evidence.

The workflow is:

Read the repository instructions and applicable specifications.
Inspect the existing implementation and establish a baseline.
Translate acceptance criteria into implementation and test tasks.
Implement the smallest appropriate change using test-driven development.
Run targeted tests and architecture verification.
Run broader verification when appropriate.
Record evidence and update migration progress.
Review the changes before proceeding to the next phase.

Do not implement the entire migration in one task.

3. Repository Guide
   Path	Purpose
   AGENTS.md	Shared repository instructions for AI coding assistants
   GEMINI.md	Gemini-specific repository guidance
   spec/spring-modulith-migration.md	Master migration specification
   spec/01-modulith-foundation.md	Phase 1: foundation and architecture verification
   spec/02-catalog-module.md	Phase 2: Catalog
   spec/03-inventory-module.md	Phase 3: Inventory
   spec/04-payments-module.md	Phase 4: Payments
   spec/05-orders-orchestration.md	Phase 5: Orders orchestration
   spec/06-api-and-deployment.md	Phase 6: API compatibility and deployment
   spec/07-migration-verification.md	Phase 7: migration verification
   spec/execution-status.md	Phase progress, blockers, and verification evidence
   ai/skills/spec-driven-development/SKILL.md	Shared SDD workflow
   ai/skills/spring-modulith/SKILL.md	Spring Modulith architecture guidance
   ai/skills/test-driven-development/SKILL.md	Test-driven development workflow
   ai/skills/migration-verification/SKILL.md	Migration verification workflow
   architecture/decisions/	Architecture Decision Records (ADRs)
   tools/verify-specs.py	Specification consistency validator
   .github/workflows/validate-specs.yml	Automated specification validation in GitHub Actions
   .github/workflows/maven-verify.yml	Automated Maven build and architecture verification in GitHub Actions

If a referenced file is missing, report the problem instead of claiming that its instructions were followed.

4. Prerequisites Before Starting

Before implementation begins, confirm that:

AGENTS.md exists and has been reviewed.
The master migration specification exists.
All seven phase specifications exist.
All four shared skills exist and have valid metadata.
Required architecture decision records exist.
spec/execution-status.md accurately records the current state.
Specification validation passes locally.
The GitHub Actions specification validation workflow passes.
The existing Java and Maven build configuration has been inspected.

Do not begin business-module migration until the foundation phase has been completed and its exit criteria verified.

5. Validate the Repository

Run the specification validator from the repository root:

python3 tools/verify-specs.py


Check Python syntax separately:

python3 -m py_compile tools/verify-specs.py


Inspect the working tree:

git status --short


Resolve validation errors before starting implementation. Review warnings and determine whether they represent genuine gaps.

The validator checks specification consistency; it does not prove that the Java application builds or that the architecture is correct.

6. Start Phase 1

The first implementation task is Phase 1: Modulith Foundation.

Read these files before asking an AI assistant to implement anything:

AGENTS.md
spec/spring-modulith-migration.md
spec/01-modulith-foundation.md
ai/skills/spec-driven-development/SKILL.md
ai/skills/spring-modulith/SKILL.md
Relevant architecture decision records.

Read the TDD and migration-verification skills when their workflows apply.

Phase 1 objectives

The foundation phase must:

Inspect the current repository, Maven configuration, Java version, and Spring Boot version.
Record existing build and test results, including pre-existing failures.
Establish a new Spring Boot application entry point using compatible dependencies.
Define the Catalog, Inventory, Orders, and Payments module boundaries.
Add Spring Modulith architecture verification.
Verify the intended module dependency rules and detect cycles.
Preserve the existing microservices and business functionality.
Document build instructions, compatibility decisions, and outstanding risks.

Phase 1 must not migrate business logic. Catalog, Inventory, Payments, and Orders business migration belongs to the subsequent phases.

Refer to spec/01-modulith-foundation.md for the complete requirements and exit criteria.

7. Prompt for Your AI Coding Assistant

Use this prompt with Claude Code, Gemini CLI, GitHub Copilot, or another coding assistant that can inspect and modify the repository.

We are using specification-driven development (SDD)
to migrate this retail application toward Spring Modulith.

Before changing any files:

1. Read AGENTS.md.
2. Read spec/spring-modulith-migration.md.
3. Read spec/01-modulith-foundation.md.
4. Read ai/skills/spec-driven-development/SKILL.md.
5. Read ai/skills/spring-modulith/SKILL.md.
6. Read the relevant architecture decision records.
7. Inspect the current working tree, source code, Maven
   configuration, tests, and database migrations.

First perform a repository assessment. Do not start
implementation until you have reported the findings.

Establish the current Java and Spring Boot versions,
application entry points, dependency-management approach,
persistence configuration, and existing build/test baseline.

Identify any conflicts between the current implementation
and the Phase 1 specification.

Then produce a Phase 1 implementation plan mapped to
specific acceptance criteria and tests.

Implement Phase 1 only after the assessment and plan.
Use test-driven development where practical.

Do not migrate Catalog, Inventory, Payments, or Orders
business logic during Phase 1.

Do not remove existing microservices, modify unrelated
functionality, or perform production cutover.

Run the relevant tests and Spring Modulith architecture
verification. Report actual command results and map each
acceptance criterion to evidence.

Do not mark Phase 1 complete until all mandatory exit
criteria have been verified and required approval recorded.

8. Test-Driven Development

For behavior changes, use the following cycle:

Red: Write a test and confirm that it fails for the intended reason.
Green: Implement the minimum behavior needed to pass.
Refactor: Improve the implementation while preserving behavior.
Verify: Run the relevant tests and architecture checks.

Do not create artificial failures when behavior is already implemented correctly. Extend existing tests where coverage is missing.

For full Maven verification, inspect the repository's build structure and use the appropriate Maven wrapper command. If the root project supports it, the standard command is:

./mvnw clean verify


Record baseline failures separately from failures introduced by the current change.

9. Migration Phase Tracking

Use spec/execution-status.md to record progress.

Allowed phase statuses:

NOT_STARTED — work has not begun.
IN_PROGRESS — implementation or verification is underway.
BLOCKED — an unresolved issue prevents progress.
COMPLETE — mandatory acceptance criteria and exit criteria are verified and required approval is recorded.

A passing build alone does not make a phase complete.

Record the commands executed, actual results, outstanding blockers, implementation PR or commit, and required reviewer approval.

10. Architecture Decision Records

Significant architectural decisions belong in architecture/decisions/.

Before changing an accepted decision:

Read the existing ADR.
Explain the reason for the proposed change.
Assess the impact on business behavior, compatibility, reliability, and data integrity.
Create a new ADR when necessary.
Obtain the required approval.

ADRs marked Proposed are not approved architectural requirements. Implementation status and ADR approval status must be tracked separately.

11. Definition of Done

A task is complete only when:

The applicable specification and acceptance criteria are identified.
The required implementation and tests are complete.
Relevant architecture checks have been executed.
The actual verification results are recorded.
Remaining risks and unverified criteria are disclosed.
The final diff has been reviewed.
Migration progress is updated when applicable.

Never report a test as passing unless it was actually executed and its result was observed.

12. Safety and Migration Boundaries
    Preserve existing business behavior unless a change is explicitly approved.
    Do not expose repositories, JPA entities, or internal implementation packages across module boundaries.
    Do not assume multiple business operations are atomic because they run in one Spring Boot process.
    Do not claim a local database rollback reverses an external payment.
    Do not modify already-applied database changesets destructively.
    Do not remove production integrations or retire existing microservices before migration verification and explicit approval.
13. Recommended Starting Point

Once all prerequisites and CI checks pass:

Start with spec/01-modulith-foundation.md.
Complete the repository assessment.
Record baseline build and test results.
Review the Phase 1 implementation plan.
Implement and verify the foundation.
Obtain review before moving to Phase 2.

The objective is a verified, buildable modular foundation—not a premature migration of all business functionality.
