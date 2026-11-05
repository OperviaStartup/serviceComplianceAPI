# BE-US-22 — Retrieve personal execution history

- Type: `feature`
- Parent story: `US-22`
- Bounded context: `Reporting & Follow-up`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

Operators review their persisted work and later decisions without confusing execution, synchronization, and compliance states.

## Acceptance criteria

- [ ] AC-01: The authenticated operator receives only Executions owned by that operator and company.
- [ ] AC-02: Each item contains date/timezone, obligation reference, Execution state, synchronization state, and latest permitted Compliance Result when present.
- [ ] AC-03: Execution state, synchronization state, and Compliance Result are separate fields and enumerations.
- [ ] AC-04: Documented filters and deterministic cursor pagination are supported.
- [ ] AC-05: Reassignments never expose another operator's private Execution history.
- [ ] AC-06: Client-only pending records are never represented as persisted server records.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-05` | `MERGED` | Pending verification on `develop` |
| `BE-US-08` | `MERGED` | Pending verification on `develop` |
| `BE-US-11` | `MERGED` | Pending verification on `develop` |
| `BE-US-25` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Ownership and tenant tests
- State separation tests
- Filter and pagination tests
- Reassignment privacy test
- No client-only record test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
