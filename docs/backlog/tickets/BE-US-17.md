# BE-US-17 — Authenticate and authorize users

- Type: `feature`
- Parent story: `US-17`
- Bounded context: `Identity & Access`
- Context type: `Generic`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A user accesses only the capabilities and company data permitted by the active account, role, scope, and resource ownership.

## Acceptance criteria

- [x] AC-01: An active account can authenticate and receives a token or session containing user ID, company ID, role, and the data required to enforce organizational scope.
- [x] AC-02: Invalid credentials and disabled accounts are rejected without revealing which credential failed.
- [x] AC-03: Every protected endpoint validates authentication, role, tenant, organizational scope, and resource ownership.
- [x] AC-04: Operators cannot perform supervisor or administrator operations, and no user can access another company's resources.
- [x] AC-05: Access and refresh expiration, rotation, logout, and revocation behavior follow one documented policy.
- [x] AC-06: Tokens and authentication secrets are excluded from logs and error bodies.

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

- Status: `IN_PROGRESS`; readiness: `READY` for final review on this branch.
- Commits: `a0ed751` (current-account bearer checks and 30-minute access token), `bc411e9` (active-account state), `3626fee` (tenant-aware sessions, migrations, authorization, and verification).
- Checks: Java 17 `clean test` passed with H2 and with PostgreSQL 16/Flyway. PostgreSQL checks include concurrent same-token rotation and an upgrade from V1/V2 seeded with linked user, obligation, execution, and evidence data through V5.
- Blockers: `BE-US-20` must call the provided suspension capability; invitation onboarding is not part of this ticket. No blocker remains for the BE-US-17 implementation itself.
- Next action: Push this feature branch for team review and integration; do not mark `DONE` until the commits are integrated and verified on the selected base.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.

## Current-code assessment (2026-11-05)

| Criterion | Verified baseline | Missing behavior |
| --- | --- | --- |
| AC-01 | `AuthService.login` returns a JWT and `JwtService` signs an email subject and role claim. | No company ID, site scope, account state, or user ID claim. |
| AC-02 | `InvalidCredentialsException` gives one response for missing email or bad password. | No disabled account state or rejection path; no focused indistinguishability test. |
| AC-03/04 | `SecurityConfig` requires authentication for non-public routes; `ObligationController.create` requires `SUPERVISOR`; execution writes compare operator ID. | No tenant or site fields in `V1__create_core_schema.sql`; authorization cannot enforce company/site isolation, and the JWT filter trusts its embedded role without an account-state check. |
| AC-05 | JWT expiration is configurable through `app.jwt.expiration-ms`. | No refresh, rotation, logout, or revocation. |
| AC-06 | Error mapping emits generic 401/403/500 bodies; the filter clears invalid JWT authentication without logging it. | No dedicated tests asserting tokens and secrets stay out of errors/logs. |

The implementation now reads the current account on each bearer request, uses its current role, scope, active state, and session version, requires authentication for `/api/v1/auth/me`, and defaults the access JWT to 30 minutes. Versioned migrations backfill active state, refresh sessions, the approved legacy company/site, and session version. Rotating seven-day refresh tokens are stored only as hashes; replay, logout, and suspension revoke sessions. Public registration is disabled pending `BE-US-20`. Tests cover JWT identity claims, invalid and disabled accounts, role changes, tenant/site/ownership isolation, token-safe errors, refresh rotation/replay/concurrency, logout, suspension/reactivation, OpenAPI paths, and the operator HTTP journey.

`/api/v1/auth/register` is currently public; its restriction and existing-client transition are owned jointly with `BE-US-20`. Existing H2 tests disable Flyway, so they do not verify the PostgreSQL tenant migration or isolation contract.
