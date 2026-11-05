# BE-US-20 — Manage users, roles, scopes, and contracted seats

- Type: `feature`
- Parent story: `US-20`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Administrators invite and manage users only within their company, authority, and contracted seat allowance.

## Acceptance criteria

- [x] AC-01: An administrator can create a secure invitation containing company, role, organizational scope, expiry, and pending status.
- [x] AC-02: Accepting a valid single-use invitation activates exactly one account and repeated acceptance returns the same outcome.
- [x] AC-03: Seat accounting has one documented rule for pending invitations and account states, and concurrent creation or acceptance cannot exceed the contracted limit.
- [x] AC-04: Administrators cannot assign roles or organizational scopes outside their authority or company.
- [x] AC-05: Suspending a user prevents new authentication without deleting historical ownership or audit records.
- [x] AC-06: Public registration is removed, restricted, or explicitly converted to invitation acceptance.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-17` | `MERGED` | `develop` merge commit `26d4c38` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Concurrent seat-allocation test
- Invitation expiry and reuse tests
- Administrator authorization tests
- Account suspension tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `PARTIAL` because the Stripe sandbox implementation remains on another feature, while this ticket consumes its approved local entitlement contract.
- Commits: `acbac7c` (subscription-backed invitations, DNI activation, Resend delivery, seat enforcement, and user administration).
- Checks: Java 17 focused H2 integration tests and the complete 25-test PostgreSQL 16/Flyway suite pass, including concurrent final-seat allocation, expiry, replay, suspension, reactivation, tenant scope, and DNI login.
- Blockers: No blocker for the BE-US-20 implementation. End-to-end Stripe webhook synchronization and a live Resend sandbox delivery require their external feature/credentials before integration can be claimed.
- Next action: Review and commit the verified implementation, then push for team review without marking `DONE` until integration and external sandbox verification.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
