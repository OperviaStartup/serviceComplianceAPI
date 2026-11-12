# BE-US-25 — Generate, assign, and reassign a Service Obligation

- Type: `feature`
- Parent story: `US-25`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Version-bound obligation instances are generated and have one eligible responsible operator with immutable assignment history.

## Acceptance criteria

- [x] AC-01: Active definitions generate exactly one obligation per documented operational occurrence and retain the source plan/definition version.
- [x] AC-02: Generation uses the documented horizon, timezone, missed-run recovery, and occurrence uniqueness rule.
- [x] AC-03: A scoped supervisor can assign an unstarted obligation to one active eligible in-scope operator.
- [x] AC-04: Reassignment records previous and new operator, actor, timestamp, and reason.
- [x] AC-05: An obligation with an execution in progress cannot be reassigned.
- [x] AC-06: Inactive, unauthorized, out-of-scope, and cross-company assignees are rejected, and concurrent attempts cannot create two current assignees.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-03` | `MERGED` | Present on current `develop` at merge `43e97ce` and migration reconciliation `dc671d8` |
| `BE-US-20` | `MERGED` | Present on current `develop` at merge `4c128f0` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Generation and recovery tests
- Assignment history test
- Concurrent assignment test
- Invalid assignee tests
- In-progress reassignment rejection
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` for team review and integration.
- Commits: `1a7ffeb`, `6663581`, `8884dc5`
- Checks: focused generation and assignment tests, full Gradle test suite, Flyway V1-V14 on PostgreSQL 16, PostgreSQL concurrent reassignment, and diff hygiene passed.
- Bounded-context check: obligation generation and assignment live under `Field Operations`; Service Planning remains the source of active plans and definitions.
- Blockers: `NONE`
- Next action: Push the feature branch and open the PR; mark `DONE` only after merge and verification on `develop`.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
