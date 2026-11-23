# BE-US-09 — Query operational status

- Type: `feature`
- Parent story: `US-09`
- Bounded context: `Reporting & Follow-up`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
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
| `BE-US-25` | `MERGED` | Merge `8553661` is an ancestor of current `develop` |
| `BE-US-05` | `MERGED` | Merge `b9191de` is an ancestor of current `develop` |
| `BE-US-07` | `MERGED` | Merge `b1484e7` is an ancestor of current `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Status derivation and precedence tests
- Boundary-time tests
- Filtered total reconciliation
- Pagination tests
- Scope isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` under the approved BE-US-09 refinement of decision 12.
- Commits: `NONE`
- Checks: Java 17 full H2 suite `115/115` with `0` failures (`11` PostgreSQL-only migration tests
  skipped); operational-status PostgreSQL integration `3/3`; PostgreSQL migration group `11/11`;
  assigned-work, operator, offline-sync, reporting, and HTTP E2E PostgreSQL classes pass in isolated
  databases; workflow YAML parses successfully and its commands were reproduced locally.
- Blockers: `NONE_LOCAL`
- Next action: Review and commit the branch, then observe the GitHub Actions workflow on the pull request.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
