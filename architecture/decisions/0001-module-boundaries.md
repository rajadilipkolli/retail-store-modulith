ADR-0001: Define Business Module Boundaries
Status: Proposed
Date: 2026-10-21
Decision owners: Repository maintainers
Related specifications: spec/01-modulith-foundation.md, spec/02-catalog-module.md, spec/03-inventory-module.md, spec/04-payments-module.md, spec/05-orders-orchestration.md
Supersedes: None
Superseded by: None
1. Context

The application is migrating from independently deployed business services toward a Spring Modulith application.

Without explicit module ownership, moving services into one codebase could create shared persistence access, cyclic dependencies, and tightly coupled implementations.

2. Decision

The target business modules are:

Catalog: product information and catalog-owned pricing behavior.
Inventory: stock availability, reservation, and release.
Payments: payment-related operations and financial state.
Orders: order lifecycle, order persistence, and cross-module orchestration.

The actual Java package names MUST follow the repository's root package and approved implementation structure.

Each module owns its domain logic and persistence.

Other modules MUST access its capabilities through documented public module APIs or approved application events.

Internal repositories, entities, and implementation packages MUST NOT be used as cross-module APIs.

3. Alternatives Considered
Alternative A: Preserve independent service implementations

This preserves existing deployment boundaries but does not achieve the intended modular monolith.

Alternative B: Combine all business logic into one shared package

This simplifies initial relocation but weakens ownership and makes future changes harder to isolate.

Alternative C: Use explicit Spring Modulith modules

This makes module ownership and permitted dependencies explicit while keeping one application deployment.

4. Consequences
Positive
Clear ownership of business logic and persistence.
Enforceable module boundaries.
Easier testing of module contracts.
Reduced accidental coupling.
Trade-offs
Public module APIs require deliberate design.
Some existing cross-service contracts may need adaptation.
Architecture tests must be maintained.
5. Implementation Guidance
Document module APIs and allowed dependencies.
Use Spring Modulith package-level module declarations where appropriate.
Add architecture verification to the Maven test lifecycle.
Preserve business behavior while migrating implementations.
6. Verification Requirements
Expected modules are discovered.
Prohibited dependencies are rejected.
Cyclic dependencies are absent.
Internal repositories are not used across module boundaries.
Public APIs expose no persistence entities.
7. Approval
Reviewed by: TBD
Approved on: TBD
Implementation status: Not verified
8. References
spec/spring-modulith-migration.md
spec/01-modulith-foundation.md
