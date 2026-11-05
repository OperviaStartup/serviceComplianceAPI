# BE-US-02 — Define obligations and Evidence Requirements

- Type: `feature`
- Parent story: `US-02`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A draft Service Plan contains explicit operational definitions and evidence rules.

## Acceptance criteria

- [ ] AC-01: An authorized administrator can add or edit an Obligation Definition only while the plan is DRAFT.
- [ ] AC-02: The definition stores site, activity, instructions, schedule/window rule, acceptance criteria, and authoritative timezone.
- [ ] AC-03: Each Evidence Requirement records required/optional status, allowed type, and documented validation constraints.
- [ ] AC-04: Evidence Requirements belong only to their specified definition and cannot leak across definitions.
- [ ] AC-05: Invalid schedules, sites, evidence types, limits, or metadata rules are rejected.
- [ ] AC-06: Changes to an active definition are rejected and must use the versioning behavior owned by US-03.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-01` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Aggregate persistence tests
- Evidence-isolation tests
- Schedule and timezone validation
- Active-plan edit rejection
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
