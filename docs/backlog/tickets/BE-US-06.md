# BE-US-06 — Attach required Evidence

- Type: `feature`
- Parent story: `US-06`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Evidence is stored securely and validated against the exact versioned requirement and attachment context.

## Acceptance criteria

- [ ] AC-01: The assigned operator can attach Evidence to that operator's Execution under the documented upload protocol.
- [ ] AC-02: Evidence records target context, type, storage reference or payload metadata, capture timestamp, actor, checksum/integrity metadata, and audit timestamps.
- [ ] AC-03: Type, size, format, metadata, and required status are validated against the applicable versioned Evidence Requirement.
- [ ] AC-04: Requirements from another obligation, definition version, company, or attachment context are never applied.
- [ ] AC-05: Submission is blocked only for genuinely required missing evidence; optional or absent requirements do not block it.
- [ ] AC-06: Operation replay does not duplicate evidence, and unauthorized or guessed downloads return no evidence or sensitive metadata.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-02` | `MERGED` | Pending verification on `develop` |
| `BE-US-05` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Required/optional evidence tests
- Format and limit validation
- Resource download authorization
- Duplicate upload test
- Missing-evidence submission tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
