Spring Modulith Migration Execution Status
Master specification: spec/spring-modulith-migration.md
Last updated: YYYY-MM-DD
Status owner: Repository maintainers
1. Purpose

Track implementation and verification progress for each migration phase.

This file is a progress record. It does not replace the master specification, phase acceptance criteria, test results, or required approvals.

An AI coding assistant MUST NOT mark a phase complete merely because its implementation files exist.

2. Status Definitions

Use exactly one of these statuses per phase:

NOT_STARTED: Work has not begun.
IN_PROGRESS: Implementation or verification is underway.
BLOCKED: Work cannot proceed because of an unresolved prerequisite, decision, or environmental limitation.
COMPLETE: All mandatory acceptance criteria and exit criteria have been verified, and required approval has been recorded.

A phase with failed or unverified mandatory criteria MUST NOT be marked COMPLETE.

3. Phase Overview
Phase	Specification	Status	Evidence / PR	Approval
1. Modulith foundation	01-modulith-foundation.md	NOT_STARTED	TBD	Pending
2. Catalog module	02-catalog-module.md	NOT_STARTED	TBD	Pending
3. Inventory module	03-inventory-module.md	NOT_STARTED	TBD	Pending
4. Payments module	04-payments-module.md	NOT_STARTED	TBD	Pending
5. Orders orchestration	05-orders-orchestration.md	NOT_STARTED	TBD	Pending
6. API and deployment	06-api-and-deployment.md	NOT_STARTED	TBD	Pending
7. Migration verification	07-migration-verification.md	NOT_STARTED	TBD	Pending

Update this table only when there is evidence supporting the change.

4. Phase Entry Requirements

Before starting a phase:

Read its specification and relevant prerequisites.
Verify that required earlier phases are complete, unless an explicitly approved parallel-work plan applies.
Record the baseline build and relevant test results.
Identify outstanding decisions and blockers.
Confirm that unrelated working-tree changes will be preserved.

If a prerequisite is missing, set the phase to BLOCKED and describe the reason.

5. Phase Completion Requirements

Before setting a phase to COMPLETE:

All mandatory acceptance criteria are PASS.
Required tests have executed successfully.
Architecture verification has passed where applicable.
Required API and data compatibility checks have passed.
The final diff has been reviewed.
Outstanding risks and deviations are documented.
Verification evidence is recorded below.
Required reviewer approval is recorded.

A passing compilation alone is insufficient.

Do not use COMPLETE to mean merely "implementation finished."

6. Phase Evidence Records

Append a record for each phase when work begins or its status changes.

Record template
Phase N: Phase title
Specification: <repository-relative path to the phase specification>
Status: NOT_STARTED
Started: YYYY-MM-DD or TBD
Completed: TBD
Implementation PR / commit: TBD
Reviewer / approver: TBD

Acceptance criteria evidence

Criterion	Status	Evidence	Notes
Criterion description	NOT VERIFIED	TBD	TBD

Verification commands

Command	Result	Notes
./mvnw clean verify	NOT VERIFIED	Not yet executed

Outstanding issues

None recorded.

Decision

Continue, remediate, or request review.
7. Blocker Register
ID	Phase	Description	Owner	Status	Resolution
B-001	TBD	No blockers recorded	TBD	OPEN	TBD

Use OPEN or RESOLVED for blocker entries.

Do not remove resolved blockers; preserve their history or link to the resolving change.

8. Architecture Decisions

Track the decisions required by the migration.

ADR	Decision	Status	Evidence
0001-module-boundaries.md	Business module ownership	Proposed	TBD
0002-module-communication.md	Cross-module communication	Proposed	TBD
0003-order-processing-workflow.md	Order orchestration	Proposed	TBD
0004-event-reliability.md	Event reliability	Proposed	TBD
0005-database-migration-strategy.md	Persistence ownership and migration	Proposed	TBD

The ADR status indicates decision approval. The phase status indicates implementation progress. These are different concepts.

9. Change Rules
Update the phase status when meaningful progress or a blocker occurs.
Record actual test commands and results.
Do not fabricate commits, PR numbers, reviewer names, or test results.
Do not mark an ADR accepted without the required review.
Do not mark a phase complete while mandatory criteria remain unverified.
Do not authorize production cutover through a status-file edit.
Keep this file concise and link to detailed reports where available.
10. Final Migration Readiness

The overall migration MUST NOT be marked complete until:

All required phase exit criteria are satisfied.
Required compatibility and data integrity checks pass.
Mandatory acceptance criteria in spec/spring-modulith-migration.md have supporting evidence.
Outstanding production risks are disclosed.
The migration verification report is complete.
Required migration and cutover approvals have been recorded.

Overall migration status: NOT_STARTED

Final verification report: TBD

Production cutover approval: Not granted