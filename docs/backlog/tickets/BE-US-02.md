# BE-US-02 — Define obligations and Evidence Requirements

- Type: `feature`
- Parent story: `US-02`
- Bounded context: `Service Planning`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A draft Service Plan contains explicit operational definitions and evidence rules.

## Acceptance criteria

- [x] AC-01: An authorized administrator can add or edit an Obligation Definition only while the plan is DRAFT.
- [x] AC-02: The definition stores site, activity, instructions, schedule/window rule, acceptance criteria, and authoritative timezone.
- [x] AC-03: Each Evidence Requirement records required/optional status, allowed type, and documented validation constraints.
- [x] AC-04: Evidence Requirements belong only to their specified definition and cannot leak across definitions.
- [x] AC-05: Invalid schedules, sites, evidence types, limits, or metadata rules are rejected.
- [x] AC-06: Changes to an active definition are rejected and must use the versioning behavior owned by US-03.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-01` | `MERGED` | Verified in `origin/develop` at merge `e1af13d` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Aggregate persistence tests
- Evidence-isolation tests
- Schedule and timezone validation
- Active-plan edit rejection
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `PENDING_LOCAL_COMMIT`
- Checks: focused `ObligationDefinitionIntegrationTests` and complete `./gradlew build` passed on
  Java 17; V9 applied to PostgreSQL 16; Hibernate validation and host startup succeeded on an
  isolated random port because the user's API already occupied port 8080.
- Blockers: `NONE`
- Next action: Review the final diff and merge the verified feature branch through a pull request.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
