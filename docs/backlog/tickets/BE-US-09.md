# BE-US-09 — Query operational status

- Type: `feature`
- Parent story: `US-09`
- Bounded context: `Reporting & Follow-up`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Supervisors identify work requiring attention within their authorized organizational scope.

## Acceptance criteria

- [ ] AC-01: Scoped supervisors can filter by service, site, period, operator, and operational status.
- [ ] AC-02: Canonical status semantics distinguish ASSIGNED, IN_PROGRESS, SUBMITTED, EXCEPTED, and OVERDUE and define precedence when conditions overlap.
- [ ] AC-03: Overdue is derived from the versioned operational window, authoritative clock, and documented timezone/boundary rule.
- [ ] AC-04: Cursor pagination and ordering remain deterministic as data changes and grows.
- [ ] AC-05: Aggregated counts use the identical authorized filters and status definitions as detail rows.
- [ ] AC-06: Out-of-scope obligations are neither returned nor counted.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |
| `BE-US-05` | `MERGED` | Pending verification on `develop` |
| `BE-US-07` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Status derivation and precedence tests
- Boundary-time tests
- Filtered total reconciliation
- Pagination tests
- Scope isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
