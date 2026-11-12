# BE-US-04 — List assigned obligations

- Type: `feature`
- Parent story: `US-04`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Operators receive only their current assigned work through a stable synchronization contract.

## Acceptance criteria

- [x] AC-01: The authenticated operator receives only obligations currently assigned to that user and company.
- [x] AC-02: Results contain site, activity, instructions, operational window and timezone, source version, and operational status.
- [x] AC-03: Date/status filters and deterministic cursor pagination use a documented stable order.
- [x] AC-04: Reassignment removes the item from the former operator and exposes it to the new operator without leaking protected details.
- [x] AC-05: Incremental synchronization uses a stable cursor or documented updatedSince boundary.
- [x] AC-06: Other operators' and companies' obligations are neither returned nor counted.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | PR #12 at `8553661`; 90-test suite verified on merged `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Ownership-filter tests
- Stable pagination tests
- Reassignment visibility test
- Synchronization cursor test
- Tenant isolation test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` for team review on a clean/current Flyway schema.
- Commits: `efd969a`
- Checks: focused assigned-work H2 and PostgreSQL integration tests passed; V15 fresh-schema migration and trigger test passed; complete H2 suite passed with 95 tests, 0 failures, and 6 opt-in migration skips.
- Compatibility: legacy `GET /api/v1/obligations` remains unchanged; the new contract is under `/api/v1/assigned-obligations`.
- Blockers: `NONE` for BE-US-04. The developer database created under the historical V10 numbering collision requires separate migration-history reconciliation before local startup; do not use blind Flyway repair.
- Next action: Review the local commits, then push and open a PR. Do not mark `DONE` until merge and verification on `develop`.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
