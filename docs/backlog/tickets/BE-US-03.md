# BE-US-03 — Activate and version a Service Plan

- Type: `feature`
- Parent story: `US-03`
- Bounded context: `Service Planning`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A valid draft becomes active and every later change remains traceable as a new immutable version.

## Acceptance criteria

- [x] AC-01: Only an authorized company administrator can activate a plan.
- [x] AC-02: Activation requires all mandatory plan data and at least one valid Obligation Definition.
- [x] AC-03: Activation records state, actor, authoritative timestamp, and an immutable version containing its definitions and requirements.
- [x] AC-04: Repeated activation of the same version is idempotent.
- [x] AC-05: A change affecting emitted obligations creates a new plan version and never rewrites historical definitions.
- [x] AC-06: Concurrent activation enforces the documented overlap rule for company, service, site, and validity period.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-02` | `MERGED` | Verified in `origin/develop` at merge `1cf89d0` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Activation validation tests
- Concurrent activation test
- Version-history test
- Historical obligation immutability test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `9bf8690`
- Checks: focused `ServicePlanActivationIntegrationTests` and complete `./gradlew build` passed on
  Java 17 (39 tests total); V10 applied to PostgreSQL 16; Hibernate validation and host startup
  succeeded on an isolated random port.
- Integration note: the original V10 was tested on an isolated branch database. After integration
  with Field Operations, the same activation migration is V12; V10 and V11 belong to Field Operations.
  Check Flyway history before applying it to any existing database.
- Blockers: `NONE`
- Next action: Review the final diff and merge the verified feature branch through a pull request.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
