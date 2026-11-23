# BE-US-09 — Query operational status

- Type: `feature`
- Parent story: `US-09`
- Bounded context: `Reporting & Follow-up`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Supervisors identify work requiring attention within their authorized organizational scope.

## Acceptance criteria

- [x] AC-01: Scoped supervisors can filter by service, site, period, operator, and operational status.
- [x] AC-02: Canonical status semantics distinguish ASSIGNED, IN_PROGRESS, SUBMITTED, EXCEPTED, and OVERDUE and define precedence when conditions overlap.
- [x] AC-03: Overdue is derived from the versioned operational window, authoritative clock, and documented timezone/boundary rule.
- [x] AC-04: Cursor pagination and ordering remain deterministic as data changes and grows.
- [x] AC-05: Aggregated counts use the identical authorized filters and status definitions as detail rows.
- [x] AC-06: Out-of-scope obligations are neither returned nor counted.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | PR #12, `8553661`. Versioned obligations, assignment and reassignment. |
| `BE-US-05` | `MERGED` | PR #15, `b9191de`. Modern execution lifecycle. |
| `BE-US-07` | `MERGED` | PR #17, `b1484e7`. Exception evidence and transactional exception counter. |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Status derivation and precedence tests
- Boundary-time tests
- Filtered total reconciliation
- Pagination tests
- Scope isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Local implementation

- Base: `develop` at `827d342`. The operator snapshot and the existing single-obligation
  detail were already available. Neither supplies the scoped supervisor list and aggregates.
- New surface: `GET /api/v1/operational-status`. See
  [operational status in the contract pack](../contracts.md#review-follow-up-and-reporting).
- V21 adds a versioned read view and a partial scope/period index. The API does not change
  source rows or operator contracts. Reverting the API leaves the additive migration safe
  to keep in the schema.
- PostgreSQL verification lives in `OperationalStatusPostgresTests`, behind the existing
  `FIELD_OPERATIONS_TEST_JDBC_URL` switch for the dedicated loopback test database.
- Verification on JDK 17 and PostgreSQL 18.4: Gradle `build` passed with 115 tests and
  no skips, including empty-schema migration, a V20 upgrade, and authenticated HTTP checks.
- Integration is pending review and verification on merged `develop`.

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
