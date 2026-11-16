# BE-US-05 — Register an Execution

- Type: `feature`
- Parent story: `US-05`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Jean-AT`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

An assigned operator records an authoritative and idempotent execution lifecycle.

## Acceptance criteria

- [x] AC-01: Only the currently assigned operator can start and submit an Execution.
- [x] AC-02: The Execution records obligation, operator, authoritative start/submission timestamps, operational result, lifecycle state, and audit data.
- [x] AC-03: Only one active Execution exists per obligation unless a documented retry policy allows another.
- [x] AC-04: Unassigned, cancelled, reassigned, or invalid-state obligations reject execution commands.
- [x] AC-05: The server owns every state transition and separates execution state from synchronization and compliance state.
- [x] AC-06: A replayed operation ID returns the original result; payload mismatch returns a conflict; concurrent starts create one business effect.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | PR #12 at `8553661`; generation and assignment verified on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Lifecycle and ownership tests
- Concurrent-start test
- Invalid-transition tests
- Idempotent retry and payload-mismatch tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` for team review against `develop` merge `eff7b06`.
- Commits: `ef30488` (execution lifecycle, seven-day command idempotency, V16, OpenAPI, and tests).
- Checks: focused H2 suite passed with 23 tests; complete H2 suite passed with 100 tests,
  0 failures, 0 errors, and 7 opt-in migration skips. The focused PostgreSQL 16 suite passed
  with Flyway V1–V16 and Hibernate validation. The opt-in PostgreSQL migration suite passed,
  including V16 uniqueness and cascade behavior.
- Compatibility: `POST /api/v1/obligations/{id}/execution` and
  `POST /api/v1/executions/{id}/submit` now require `Idempotency-Key`; the legacy
  `POST /api/v1/executions` contract remains unchanged.
- Blockers: `NONE` for implementation. Kotlin consumers must add the required header before release.
- Next action: Review the local commits, push, and open a PR. Do not mark `DONE` until merge and
  verification on `develop`.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
