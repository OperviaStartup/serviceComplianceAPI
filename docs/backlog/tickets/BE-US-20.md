# BE-US-20 — Manage users, roles, scopes, and contracted seats

- Type: `feature`
- Parent story: `US-20`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Administrators invite and manage users only within their company, authority, and contracted seat allowance.

## Acceptance criteria

- [ ] AC-01: An administrator can create a secure invitation containing company, role, organizational scope, expiry, and pending status.
- [ ] AC-02: Accepting a valid single-use invitation activates exactly one account and repeated acceptance returns the same outcome.
- [ ] AC-03: Seat accounting has one documented rule for pending invitations and account states, and concurrent creation or acceptance cannot exceed the contracted limit.
- [ ] AC-04: Administrators cannot assign roles or organizational scopes outside their authority or company.
- [ ] AC-05: Suspending a user prevents new authentication without deleting historical ownership or audit records.
- [ ] AC-06: Public registration is removed, restricted, or explicitly converted to invitation acceptance.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-17` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Concurrent seat-allocation test
- Invitation expiry and reuse tests
- Administrator authorization tests
- Account suspension tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
