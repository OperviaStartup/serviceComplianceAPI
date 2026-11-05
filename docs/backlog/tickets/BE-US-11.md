# BE-US-11 — Record a Compliance Result

- Type: `feature`
- Parent story: `US-11`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A supervisor records an auditable, immutable compliance decision without rewriting source facts.

## Acceptance criteria

- [ ] AC-01: Only an authorized scoped supervisor can evaluate a reviewable Execution.
- [ ] AC-02: The command creates or associates the Compliance Case according to the documented case-creation rule.
- [ ] AC-03: Supported outcomes include COMPLIANT, EXCEPTION_ACCEPTED, and NON_COMPLIANT with explicit eligibility rules.
- [ ] AC-04: A decision records structured criteria, findings, explanation, actor, authoritative timestamp, source review version, and plan version.
- [ ] AC-05: Existing results are immutable; reevaluation appends a new version linked to its predecessor and reason.
- [ ] AC-06: Concurrent evaluations use an explicit expected version and cannot silently overwrite one another.
- [ ] AC-07: Execution, Evidence, and Exceptions remain unchanged by a decision.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-10` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Outcome and eligibility tests
- Accepted-exception test
- Case-creation test
- Decision versioning and concurrency tests
- Immutable-source-data test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
