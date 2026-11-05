# BE-US-23 — List operator notifications

- Type: `feature`
- Parent story: `US-23`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Operators retrieve an isolated inbox of assignment, corrective, and synchronization notifications.

## Acceptance criteria

- [ ] AC-01: An operator receives notifications only for that recipient and resources the recipient may currently access.
- [ ] AC-02: Typed events identify the permitted obligation or corrective-action reference and a stable reason code.
- [ ] AC-03: Read/unread state and timestamps are maintained independently per recipient.
- [ ] AC-04: Cursor pagination and unread counts use identical recipient and visibility filters.
- [ ] AC-05: Synchronization notifications use a type and fields distinct from Compliance Results.
- [ ] AC-06: Reassignment prevents the former assignee from retrieving protected details while retaining only explicitly permitted historical notification metadata.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-16` | `MERGED` | Pending verification on `develop` |
| `BE-US-08` | `MERGED` | Pending verification on `develop` |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |
| `BE-US-21` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Recipient isolation tests
- Read-state tests
- Pagination/count reconciliation
- Event typing test
- Reassignment privacy tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
