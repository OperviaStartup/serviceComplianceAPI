# BE-US-01 — Create a Service Plan

- Type: `feature`
- Parent story: `US-01`
- Bounded context: `Service Planning`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

An administrator creates a company-scoped draft plan for an authorized service and site.

## Acceptance criteria

- [x] AC-01: Creating a plan stores its ID, company, service, site, validity period, creator, audit timestamps, and DRAFT state.
- [x] AC-02: Required fields, authorized service/site references, timezone, and date-range rules return field-specific errors.
- [x] AC-03: Reusing an idempotency key with the same payload returns the original plan; a different payload returns a conflict.
- [x] AC-04: Only authorized administrators within the company can create or view the plan.
- [x] AC-05: A draft exposes its missing activation requirements without becoming active.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-17` | `MERGED` | Verified in `origin/develop` through merge `26d4c38` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Creation and validation tests
- Idempotency payload-mismatch test
- Tenant isolation test
- Invalid-period boundary tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `c990e43`
- Checks: focused `ServicePlanIntegrationTests` and complete `./gradlew test` passed on Java 17;
  V7 executed successfully against PostgreSQL 16 inside a rolled-back temporary-schema transaction.
- Blockers: `NONE`
- Next action: Review the final diff and merge the verified feature branch through a pull request.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
