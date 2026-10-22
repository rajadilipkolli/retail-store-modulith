ADR-0006: Define Canonical Money Policy and Financial Correctness
Status: Proposed
Date: 2026-10-22
Decision owners: Repository maintainers
Related specifications: spec/04-payments-module.md, spec/05-orders-orchestration.md
Supersedes: None
Superseded by: None
1. Context

The Payments module is migrating from payment-service into the Spring Modulith architecture.

In distributed retail systems, monetary calculations and balances must be consistent across storage, APIs, events, and accounting. Floating-point representations (such as double or float) can cause rounding errors and financial discrepancies. Moreover, implicit currency conversions, undefined rounding modes, or lack of currency validation can lead to silent financial loss or data corruption.

2. Decision

The application establishes a canonical money policy for the Payments module and all order processing:

1. Currency: The supported currency is USD. Any request with missing or unsupported currency MUST be rejected with a validation error without state mutation.
2. Representation and Precision: Monetary values MUST use java.math.BigDecimal in application code and NUMERIC(19,2) in PostgreSQL persistence.
3. Scale and Rounding: All amounts MUST have an exact scale of 2 decimal places. Input values with excess precision are rounded using RoundingMode.HALF_UP.
4. Non-Negative Invariants: Payment amounts for reservations, confirmations, releases, and refunds must be strictly positive (amount > 0). Customer available and reserved balances MUST never be negative, enforced by database check constraints:
   - chk_customers_amount_available CHECK (amount_available >= 0)
   - chk_customers_amount_reserved CHECK (amount_reserved >= 0)
5. Currency Mismatches: Currency mismatch between requests, accounts, or previous transactions MUST be rejected with CurrencyMismatchException. Implicit currency conversion is strictly prohibited.
6. Financial State Transitions: Explicit lifecycle states:
   - RESERVE: Locks available funds and moves them to reserved funds.
   - CONFIRM: Captures reserved funds.
   - RELEASE: Restores reserved funds back to available funds.
   - REFUND: Credits captured funds back to available funds with reference to the original payment.

3. Alternatives Considered
Alternative A: Floating-point types (Double / Float)

Rejected due to IEEE 754 precision issues and rounding errors in financial transactions.

Alternative B: Multi-currency support with dynamic conversion

Rejected as unnecessary for the current business model and introduces currency exchange rate risks and external dependencies.

Alternative C: Strict BigDecimal with RoundingMode.HALF_UP and database check constraints

Adopted. Guarantees exact cents precision, standard retail rounding, and database-enforced consistency.

4. Consequences
Positive
Predictable, reproducible rounding and financial calculations.
Database check constraints prevent balance overdrafts even under concurrency anomalies.
Zero risk of cross-currency contamination.
Trade-offs
String or numeric serialization must preserve 2 decimal places.
Callers must supply USD currency or adhere to default.
5. Verification Requirements
Unit tests verifying RoundingMode.HALF_UP rounding ties and excess precision handling.
Unit and integration tests verifying rejection of negative or zero amounts.
Unit and integration tests verifying rejection of unsupported currencies and currency mismatches.
Database integration tests verifying check constraint enforcement on negative balances.
Concurrent reservation tests verifying zero overdrafts and zero double-spend.
6. Approval
Reviewed by: Repository maintainers
Approved on: Pending
Implementation status: In progress
7. References
spec/04-payments-module.md
