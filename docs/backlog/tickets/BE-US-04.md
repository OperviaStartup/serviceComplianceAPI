# BE-US-04 — List assigned obligations

- Type: `feature`
- Parent story: `US-04`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Operators receive only their current assigned work through a stable synchronization contract.

## Acceptance criteria

- [ ] AC-01: The authenticated operator receives only obligations currently assigned to that user and company.
- [ ] AC-02: Results contain site, activity, instructions, operational window and timezone, source version, and operational status.
- [ ] AC-03: Date/status filters and deterministic cursor pagination use a documented stable order.
- [ ] AC-04: Reassignment removes the item from the former operator and exposes it to the new operator without leaking protected details.
- [ ] AC-05: Incremental synchronization uses a stable cursor or documented updatedSince boundary.
- [ ] AC-06: Other operators' and companies' obligations are neither returned nor counted.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Ownership-filter tests
- Stable pagination tests
- Reassignment visibility test
- Synchronization cursor test
- Tenant isolation test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
