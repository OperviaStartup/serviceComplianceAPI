# BE-US-07 — Report an Exception

- Type: `feature`
- Parent story: `US-07`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

An assigned operator records why an obligation could not proceed without creating a compliance decision.

## Acceptance criteria

- [x] AC-01: Only the assigned operator can register an Exception with an allowed reason, description, authoritative timestamp, and permitted evidence references.
- [x] AC-02: The Exception remains linked to its Execution, obligation, operator, and company.
- [x] AC-03: Exception creation exposes an immutable reviewable record while the Execution remains `IN_PROGRESS` under the approved lifecycle contract.
- [x] AC-04: Creating an Exception never automatically creates a NON_COMPLIANT Compliance Result.
- [x] AC-05: Replaying the same operation returns the original Exception without duplication.
- [x] AC-06: Invalid reasons, invalid lifecycle states, and another operator's or company's obligation are rejected.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-05` | `MERGED` | Merge `b9191de`; lifecycle and regression suites pass on this branch |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Exception lifecycle tests
- Authorization and tenant tests
- Idempotency tests
- No automatic non-compliance test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` for team review.
- Commits: `NONE`; implementation is present in the local working tree and has not been committed.
- Checks: full H2 regression 106 tests, 97 executed and 9 opt-in skips, with zero failures;
  focused H2 flow suite 27/27; PostgreSQL 16 HTTP/Flyway/Hibernate suite 27/27; PostgreSQL
  migration suite 6/6 including V18→V19.
- Compatibility: `POST /api/v1/executions/{id}/exceptions` now requires `Idempotency-Key` and
  retains body `operationId`; optional `evidenceIds` only accept immutable evidence from the same
  execution. The execution remains `IN_PROGRESS`, and no Compliance Result is created.
- Blockers: `NONE_FOR_LOCAL_REVIEW`; merge and verification on `develop` remain required for `DONE`.
- Next action: Review the diff, create a focused Conventional Commit, push, and open a PR against
  `develop`. Do not mark `DONE` until merge and verification on `develop`.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
