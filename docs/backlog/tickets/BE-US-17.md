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

- Status: `DRAFT`; readiness: `PARTIAL` (invalid-credential and secret-safe error behavior can be verified independently; tenant, scope, lifecycle, and revocation behavior requires accepted contracts).
- Commits: `NONE` for implementation.
- Checks: `NOT_RUN` on this ticket. Prior baseline `bash gradlew test` failed before compilation because the host has Java 25 and `build.gradle` requests Java 17.
- Blockers: Contract decisions 1–3 in `docs/backlog/contracts.md` are drafts; Java 17 verification environment is unavailable locally.
- Next action: Accept the role/scope, error, and session policies with exact token lifetimes; obtain Java 17; implement and verify AC-01–AC-06 in slices.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.

## Current-code assessment (2026-11-05)

| Criterion | Verified baseline | Missing behavior |
| --- | --- | --- |
| AC-01 | `AuthService.login` returns a JWT and `JwtService` signs an email subject and role claim. | No company ID, site scope, account state, or user ID claim. |
| AC-02 | `InvalidCredentialsException` gives one response for missing email or bad password. | No disabled account state or rejection path; no focused indistinguishability test. |
| AC-03/04 | `SecurityConfig` requires authentication for non-public routes; `ObligationController.create` requires `SUPERVISOR`; execution writes compare operator ID. | No tenant or site fields in `V1__create_core_schema.sql`; authorization cannot enforce company/site isolation, and the JWT filter trusts its embedded role without an account-state check. |
| AC-05 | JWT expiration is configurable through `app.jwt.expiration-ms`. | No refresh, rotation, logout, or revocation. |
| AC-06 | Error mapping emits generic 401/403/500 bodies; the filter clears invalid JWT authentication without logging it. | No dedicated tests asserting tokens and secrets stay out of errors/logs. |

`/api/v1/auth/register` is currently public; its restriction and existing-client transition are owned jointly with `BE-US-20`. Existing H2 tests disable Flyway, so they do not verify the PostgreSQL tenant migration or isolation contract.
