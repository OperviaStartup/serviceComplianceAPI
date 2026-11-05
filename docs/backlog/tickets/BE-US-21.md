# BE-US-21 — Submit corrective-action attention

- Type: `feature`
- Parent story: `US-21`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

The assigned operator submits corrective work and evidence for supervisor verification.

## Acceptance criteria

- [ ] AC-01: Only the current assigned operator can submit attention details and corrective-action evidence.
- [ ] AC-02: Corrective evidence uses the common secure evidence capability but never an Execution's Evidence Requirement accidentally.
- [ ] AC-03: Submission moves the action to the documented verification-pending state.
- [ ] AC-04: An authorized supervisor can verify or return the submission with a required reason.
- [ ] AC-05: Return and resubmission append traceable lifecycle events and never modify the original Compliance Result.
- [ ] AC-06: Replaying an offline submission returns the original outcome without duplicate evidence or transitions.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-12` | `MERGED` | Pending verification on `develop` |
| `BE-US-20` | `MERGED` | Pending verification on `develop` |
| `BE-US-06` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Assignee authorization tests
- Corrective evidence isolation
- Verify/return/resubmit lifecycle tests
- Idempotency test
- Original-result preservation test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
