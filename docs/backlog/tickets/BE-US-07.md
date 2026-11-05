# BE-US-07 — Report an Exception

- Type: `feature`
- Parent story: `US-07`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

An assigned operator records why an obligation could not proceed without creating a compliance decision.

## Acceptance criteria

- [ ] AC-01: Only the assigned operator can register an Exception with an allowed reason, description, authoritative timestamp, and permitted evidence references.
- [ ] AC-02: The Exception remains linked to its Execution, obligation, operator, and company.
- [ ] AC-03: Exception creation moves the Execution into the documented reviewable state.
- [ ] AC-04: Creating an Exception never automatically creates a NON_COMPLIANT Compliance Result.
- [ ] AC-05: Replaying the same operation returns the original Exception without duplication.
- [ ] AC-06: Invalid reasons, invalid lifecycle states, and another operator's or company's obligation are rejected.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-05` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Exception lifecycle tests
- Authorization and tenant tests
- Idempotency tests
- No automatic non-compliance test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
