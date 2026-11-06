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

- [ ] AC-01: Active definitions generate exactly one obligation per documented operational occurrence and retain the source plan/definition version.
- [ ] AC-02: Generation uses the documented horizon, timezone, missed-run recovery, and occurrence uniqueness rule.
- [ ] AC-03: A scoped supervisor can assign an unstarted obligation to one active eligible in-scope operator.
- [ ] AC-04: Reassignment records previous and new operator, actor, timestamp, and reason.
- [ ] AC-05: An obligation with an execution in progress cannot be reassigned.
- [ ] AC-06: Inactive, unauthorized, out-of-scope, and cross-company assignees are rejected, and concurrent attempts cannot create two current assignees.

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

- Status: `IN_REVIEW`
- Commits: `LOCAL_FEATURE_BRANCH_PENDING_COMMIT`
- Checks: `compileJava`, focused generation tests, full existing test suite, Flyway V13/V14 startup, and OpenAPI route verification passed
- Blockers: `NONE_KNOWN`
- Next action: Review the local diff and commit; PostgreSQL concurrency coverage remains a follow-up before merge.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
