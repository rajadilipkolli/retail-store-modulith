Architecture Decision Records
Purpose

Architecture Decision Records (ADRs) document important architectural decisions, their context, alternatives, consequences, and current status.

ADRs are the shared architectural decision history for this repository.

Naming Convention

Use this format:

NNNN-short-description.md

Examples:

0001-module-boundaries.md
0002-module-communication.md
0003-order-processing-workflow.md

Use sequential numbers. Do not reuse a number, even when an ADR is superseded.

Required Sections

Every ADR MUST contain:

Title and number.
Status.
Date.
Context.
Decision.
Alternatives considered.
Consequences.
Verification requirements.
Related specifications.
Status Values

Use one of the following:

Proposed: Under discussion; not approved.
Accepted: Approved and intended to guide implementation.
Rejected: Considered but not adopted.
Superseded: Replaced by another ADR.

An ADR marked Accepted records an approved decision, not proof that implementation is complete.

Rules
Create an ADR for significant architecture decisions.
Keep the record concise and specific.
Record alternatives and explain why the decision was selected.
Link relevant specifications and related ADRs.
Update implementation and tests to match accepted decisions.
Do not silently change an accepted decision.
When a decision changes, create a new ADR and mark the old one Superseded.
Obtain required review before accepting consequential decisions.
Keep implementation status in spec/execution-status.md, not in the ADR status field.
Review Checklist

Before accepting an ADR, verify:

The problem and context are clear.
The decision is explicit.
Alternatives have been considered.
Consequences and trade-offs are documented.
Relevant specifications are referenced.
Security, reliability, and data integrity implications are considered.
Required verification is identified.
The decision has the required approval.