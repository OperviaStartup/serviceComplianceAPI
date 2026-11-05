# BE-US-05 — Register an Execution

- Type: `feature`
- Parent story: `US-05`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

An assigned operator records an authoritative and idempotent execution lifecycle.

## Acceptance criteria

- [ ] AC-01: Only the currently assigned operator can start and submit an Execution.
- [ ] AC-02: The Execution records obligation, operator, authoritative start/submission timestamps, operational result, lifecycle state, and audit data.
- [ ] AC-03: Only one active Execution exists per obligation unless a documented retry policy allows another.
- [ ] AC-04: Unassigned, cancelled, reassigned, or invalid-state obligations reject execution commands.
- [ ] AC-05: The server owns every state transition and separates execution state from synchronization and compliance state.
- [ ] AC-06: A replayed operation ID returns the original result; payload mismatch returns a conflict; concurrent starts create one business effect.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Lifecycle and ownership tests
- Concurrent-start test
- Invalid-transition tests
- Idempotent retry and payload-mismatch tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
