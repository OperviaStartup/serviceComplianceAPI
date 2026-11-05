# BE-US-13 — Record a Client Observation

- Type: `feature`
- Parent story: `US-13`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Later client feedback is associated with the correct case without rewriting earlier decisions.

## Acceptance criteria

- [ ] AC-01: An authorized scoped supervisor records description, origin, observed date, author, audit timestamp, and case association.
- [ ] AC-02: The original and later Compliance Results remain immutable.
- [ ] AC-03: An observation-triggered reevaluation uses the versioning behavior owned by US-11 and records the observation as its reason.
- [ ] AC-04: Observations are append-only; a correction creates an audited amendment linked to the original.
- [ ] AC-05: Cross-company, out-of-scope, and unrelated-case associations are rejected.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-11` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Observation creation test
- Amendment audit test
- Observation-linked reevaluation test
- Tenant and scope isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
