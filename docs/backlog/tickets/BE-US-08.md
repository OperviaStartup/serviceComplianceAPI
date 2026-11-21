# BE-US-08 — Synchronize offline records safely

- Type: `feature`
- Parent story: `US-08`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `13` points (planning baseline; refine with delivery team)

## Objective

The backend receives offline Execution, Evidence, and Exception operations without duplication or silent data loss.

## Acceptance criteria

- [x] AC-01: Every item carries a stable client operation ID under the documented scope and retention policy.
- [x] AC-02: Replaying the same operation and payload returns the original result; a changed payload returns a machine-readable conflict.
- [x] AC-03: A documented batch supports client references and dependency ordering without requiring server IDs the client could not know offline.
- [x] AC-04: Optimistic concurrency conflicts return current version information and never overwrite server changes silently.
- [x] AC-05: Every item returns SYNCED, RETRYABLE, or REQUIRES_ATTENTION with a stable reason code.
- [x] AC-06: Partial failure preserves successful items, does not mark failed items synchronized, and can recover after interruption without duplicate effects.
- [x] AC-07: Synchronization state remains separate from Execution state and Compliance Result.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-05` | `MERGED` | Present in branch history at `b9191de` |
| `BE-US-06` | `MERGED` | Present in branch history at `26b2e55` |
| `BE-US-07` | `MERGED` | Present in branch history at `b1484e7` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Replay and payload-mismatch tests
- Reordered dependency tests
- Partial-failure tests
- Optimistic-conflict tests
- Interrupted-request recovery tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `NONE`
- Checks: Java 17 H2 suite 111 total / 101 executed / 10 PostgreSQL opt-in skipped, with zero
  failures; PostgreSQL 16 validates 4/4 offline HTTP tests with Flyway V1–V20 and Hibernate
  `validate`, plus 7/7 migration tests including V20 constraints.
- Blockers: `NONE_LOCAL`
- Next action: Review the verified diff, then commit on request.
- Source: Approved decision 11 in [the contract pack](../contracts.md) and implementation evidence
  on `feature/be-us-08-offline-sync`.
