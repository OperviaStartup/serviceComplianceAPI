# BE-US-13 — Record a Client Observation

- Type: `feature`
- Parent story: `US-13`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Later client feedback is associated with the correct case without rewriting earlier decisions.

## Acceptance criteria

- [x] AC-01: An authorized scoped supervisor records description, origin, observed date, author, audit timestamp, and case association.
- [x] AC-02: The original and later Compliance Results remain immutable.
- [x] AC-03: An observation-triggered reevaluation uses the versioning behavior owned by US-11 and records the observation as its reason.
- [x] AC-04: Observations are append-only; a correction creates an audited amendment linked to the original.
- [x] AC-05: Cross-company, out-of-scope, and unrelated-case associations are rejected.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-11` | `MERGED` | Merge `ddca9f4` is present on the branch base |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Observation creation test
- Amendment audit test
- Observation-linked reevaluation test
- Tenant and scope isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` under the approved BE-US-13 contract.
- Commits: `NONE`
- Checks: complete H2 regression discovered `141` tests, executed `126`, skipped `15`
  PostgreSQL-only tests, and reported `0` failures; focused PostgreSQL integration passed `9/9`;
  the complete PostgreSQL migration gate and V24-to-V25 verification pass.
- Migration: `V25__add_client_observations.sql` adds immutable observation revisions and the unique
  observation-to-result link without changing an applied migration.
- Blockers: `NONE_LOCAL`
- Next action: Review the diff and create the requested Conventional Commit before push/PR.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
