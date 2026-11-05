# BE-US-16 — Generate supervisor alerts

- Type: `feature`
- Parent story: `US-16`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Relevant operational events generate one durable, authorized alert per recipient and occurrence.

## Acceptance criteria

- [ ] AC-01: Configured rules cover upcoming, overdue, submitted-for-review, exception, corrective-assignment, corrective-submission, and corrective-overdue events.
- [ ] AC-02: Recipient and organizational scope are resolved at occurrence time under a documented rule.
- [ ] AC-03: A qualifying occurrence creates at most one alert per recipient, rule, resource, and occurrence identity.
- [ ] AC-04: Non-qualifying or stale state changes generate no alert.
- [ ] AC-05: Alert creation is transactional or recoverable across retries and delivery failure can retry without duplicate notifications.
- [ ] AC-06: Alert records, logs, and delivery payloads contain only information permitted to the recipient.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-09` | `MERGED` | Pending verification on `develop` |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |
| `BE-US-05` | `MERGED` | Pending verification on `develop` |
| `BE-US-07` | `MERGED` | Pending verification on `develop` |
| `BE-US-12` | `MERGED` | Pending verification on `develop` |
| `BE-US-21` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Rule boundary tests
- Recipient and authorization tests
- Deduplication and retry tests
- False-positive tests
- Failure-recovery test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
