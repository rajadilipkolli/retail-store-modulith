ADR-0005: Define Database Migration and Ownership Strategy
Status: Proposed
Date: 2026-10-21
Decision owners: Repository maintainers
Related specifications: spec/01-modulith-foundation.md, spec/02-catalog-module.md, spec/03-inventory-module.md, spec/04-payments-module.md, spec/05-orders-orchestration.md, spec/07-migration-verification.md
Supersedes: None
Superseded by: None
1. Context

The original microservices may own separate databases, schemas, or sets of tables.

Moving business logic into one application does not automatically resolve database ownership, transaction boundaries, data migration, or rollback concerns.

2. Decision

Each business module MUST own its persistence model and database access.

The migration MUST first inspect the existing database arrangement and document the intended target topology.

The target implementation MUST NOT assume that a shared application process requires one shared database schema.

Use Liquibase when it is the established migration mechanism.

Database changes MUST preserve required data and identifiers unless an approved migration explicitly changes them.

The physical database layout, schema-sharing strategy, and production migration sequence remain subject to verification against the existing repository and deployment requirements.

3. Alternatives Considered
Alternative A: Preserve separate databases

This preserves stronger physical isolation but may retain cross-database workflow and operational complexity.

Alternative B: Share a database with clear module ownership

This may simplify deployment and local transactions while requiring explicit restrictions on cross-module table access.

Alternative C: Use separate schemas in one database

This can preserve some ownership boundaries while sharing database infrastructure, subject to database and transaction requirements.

4. Consequences
Positive
Explicit persistence ownership.
Documented migration requirements.
Better protection against accidental cross-module data access.
Trade-offs
Data migration and rollback may require dedicated procedures.
A shared database can weaken isolation unless access rules are enforced.
Production migration requires backup and recovery planning.
5. Implementation Guidance
Inventory the existing databases, schemas, and changesets.
Identify which module owns each table.
Avoid direct repository access across modules.
Add forward-only changesets rather than modifying changesets already applied in shared environments.
Test initialization and upgrades against representative data.
Document backups, cutover, and rollback constraints.
Never run destructive migration verification against production data.
Standardize primary key generation on TSID (Time-Sorted Unique Identifiers) via @Id @Tsid(TsidSupplier.class) from hypersistence-utils-hibernate-73 across all business modules to eliminate database sequence round-trips while providing ordered 64-bit integer IDs.
6. Verification Requirements
Existing database topology is documented.
Target persistence ownership is documented.
Empty-database initialization is tested.
Representative upgrade paths are tested.
Required data and identifiers are preserved.
Database constraints are verified.
Backup and rollback procedures are documented.
Production migration requires explicit approval.
7. Approval
Reviewed by: TBD
Approved on: TBD
Implementation status: Not verified
8. References
spec/01-modulith-foundation.md
spec/07-migration-verification.md