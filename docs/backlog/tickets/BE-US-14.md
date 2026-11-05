# BE-US-14 — Retrieve the complete Compliance Case timeline

- Type: `feature`
- Parent story: `US-14`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Supervisors reconstruct an entire case through an authorized, ordered, immutable timeline.

## Acceptance criteria

- [ ] AC-01: The timeline includes obligation, Execution, Evidence references, Exceptions, Compliance Results, observations/amendments, corrective actions, submissions, returns, and verification events.
- [ ] AC-02: Every event has a stable ID, versioned event type, actor, authoritative timestamp, and permitted resource references.
- [ ] AC-03: Ordering uses timestamp plus a stable documented tie-breaker.
- [ ] AC-04: Closing a corrective action leaves every original and superseded Compliance Result visible.
- [ ] AC-05: Cursor navigation over the stable order omits and duplicates no event.
- [ ] AC-06: Only authorized scoped supervisors can retrieve the case or its protected references.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-11` | `MERGED` | Pending verification on `develop` |
| `BE-US-12` | `MERGED` | Pending verification on `develop` |
| `BE-US-13` | `MERGED` | Pending verification on `develop` |
| `BE-US-21` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Full timeline fixture
- Equal-timestamp ordering test
- Cursor pagination test
- Original-result preservation test
- Authorization tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
