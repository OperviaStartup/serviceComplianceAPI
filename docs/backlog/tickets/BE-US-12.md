# BE-US-12 — Create and supervise a Corrective Action

- Type: `feature`
- Parent story: `US-12`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A supervisor assigns corrective work while preserving the original compliance decision and complete lifecycle history.

## Acceptance criteria

- [ ] AC-01: An authorized scoped supervisor can create a Corrective Action only for an eligible Compliance Case under the documented multiplicity rule.
- [ ] AC-02: It records case, eligible assignee, description, target date/timezone, state, actor, and audit timestamps.
- [ ] AC-03: The server enforces one documented transition matrix and optimistic concurrency rule.
- [ ] AC-04: Closing requires supervisor verification; an operator cannot self-close the action.
- [ ] AC-05: Completing, returning, or closing an action never replaces or edits a Compliance Result.
- [ ] AC-06: Overdue is derived deterministically from target date, authoritative clock, and lifecycle state.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-11` | `MERGED` | Pending verification on `develop` |
| `BE-US-20` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Creation and eligibility tests
- Transition-matrix and concurrency tests
- Operator restriction test
- Overdue boundary test
- Decision-preservation test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
