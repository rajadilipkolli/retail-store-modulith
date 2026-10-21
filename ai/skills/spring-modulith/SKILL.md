---
name: spring-modulith
description: Use when designing, implementing, testing, or reviewing Spring Modulith modules, module boundaries, application events, and architecture verification.
---

# Spring Modulith Engineering Skill

## Required context

Before implementation, read:

- spec/spring-modulith-migration.md
- The specification for the active migration phase
- Relevant repository instructions

## Workflow

1. Inspect the existing implementation before proposing changes.
2. Verify Spring Boot and Spring Modulith compatibility.
3. Identify module ownership and permitted dependencies.
4. Keep module internals inaccessible to other modules.
5. Use public module APIs for synchronous communication.
6. Use application events only where asynchronous communication is appropriate.
7. Define transaction, retry, idempotency, and recovery behavior.
8. Write tests before implementing behavior changes.
9. Run architecture verification and relevant Maven tests.
10. Report changes, test results, and unresolved risks.

## Constraints

- Do not invent repository APIs or dependency versions.
- Do not expose persistence entities across module boundaries.
- Do not remove existing services or data without approval.
- Do not claim tests passed unless they were executed successfully.
- Follow the active phase's acceptance criteria.
- Shared module rule: Cross-cutting models, common DTOs, and shared abstractions belong in the shared module (OPEN module). If any module needs to reference another module's common contracts, use the shared module.
- UI module rule: Presentation components, web views, and UI controllers belong in the ui module. Business modules MUST NOT depend on the ui module.