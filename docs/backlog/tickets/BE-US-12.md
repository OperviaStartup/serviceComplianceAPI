# BE-US-12 — Create and supervise a Corrective Action

- Type: `feature`
- Parent story: `US-12`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A supervisor assigns corrective work while preserving the original compliance decision and complete lifecycle history.

## Acceptance criteria

- [x] AC-01: An authorized scoped supervisor can create a Corrective Action only for an eligible Compliance Case under the documented multiplicity rule.
- [x] AC-02: It records case, eligible assignee, description, target date/timezone, state, actor, and audit timestamps.
- [x] AC-03: The server enforces one documented transition matrix and optimistic concurrency rule.
- [x] AC-04: Closing requires supervisor verification; an operator cannot self-close the action.
- [x] AC-05: Completing, returning, or closing an action never replaces or edits a Compliance Result.
- [x] AC-06: Overdue is derived deterministically from target date, authoritative clock, and lifecycle state.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-11` | `MERGED` | `ddca9f4` on `develop` |
| `BE-US-20` | `MERGED` | `4c128f0` on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Creation and eligibility tests
- Transition-matrix and concurrency tests
- Operator restriction test
- Overdue boundary test
- Decision-preservation test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `NONE`
- Checks: `CorrectiveActionIntegrationTests` on H2 and PostgreSQL; `CorrectiveActionMigrationTests`
  on PostgreSQL; complete H2 regression; `git diff --check`
- Blockers: `NONE`
- Next action: Review, commit and open the pull request.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
