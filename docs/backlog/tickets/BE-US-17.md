# BE-US-17 — Authenticate and authorize users

- Type: `feature`
- Parent story: `US-17`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A user accesses only the capabilities and company data permitted by the active account, role, scope, and resource ownership.

## Acceptance criteria

- [ ] AC-01: An active account can authenticate and receives a token or session containing user ID, company ID, role, and the data required to enforce organizational scope.
- [ ] AC-02: Invalid credentials and disabled accounts are rejected without revealing which credential failed.
- [ ] AC-03: Every protected endpoint validates authentication, role, tenant, organizational scope, and resource ownership.
- [ ] AC-04: Operators cannot perform supervisor or administrator operations, and no user can access another company's resources.
- [ ] AC-05: Access and refresh expiration, rotation, logout, and revocation behavior follow one documented policy.
- [ ] AC-06: Tokens and authentication secrets are excluded from logs and error bodies.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| None | — | Catalog baseline |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Authentication integration tests
- Complete role/resource matrix
- Expired, invalid, and revoked token tests
- Cross-company and cross-user isolation tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
