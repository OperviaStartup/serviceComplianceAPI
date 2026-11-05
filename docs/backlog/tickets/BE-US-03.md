# BE-US-03 — Activate and version a Service Plan

- Type: `feature`
- Parent story: `US-03`
- Bounded context: `Service Planning`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A valid draft becomes active and every later change remains traceable as a new immutable version.

## Acceptance criteria

- [ ] AC-01: Only an authorized company administrator can activate a plan.
- [ ] AC-02: Activation requires all mandatory plan data and at least one valid Obligation Definition.
- [ ] AC-03: Activation records state, actor, authoritative timestamp, and an immutable version containing its definitions and requirements.
- [ ] AC-04: Repeated activation of the same version is idempotent.
- [ ] AC-05: A change affecting emitted obligations creates a new plan version and never rewrites historical definitions.
- [ ] AC-06: Concurrent activation enforces the documented overlap rule for company, service, site, and validity period.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-02` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Activation validation tests
- Concurrent activation test
- Version-history test
- Historical obligation immutability test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
