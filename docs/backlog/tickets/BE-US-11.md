# BE-US-11 — Record a Compliance Result

- Type: `feature`
- Parent story: `US-11`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

A supervisor records an auditable, immutable compliance decision without rewriting source facts.

## Acceptance criteria

- [x] AC-01: Only an authorized scoped supervisor can evaluate a reviewable Execution.
- [x] AC-02: The command creates or associates the Compliance Case according to the documented case-creation rule.
- [x] AC-03: Supported outcomes include COMPLIANT, EXCEPTION_ACCEPTED, and NON_COMPLIANT with explicit eligibility rules.
- [x] AC-04: A decision records structured criteria, findings, explanation, actor, authoritative timestamp, source review version, and plan version.
- [x] AC-05: Existing results are immutable; reevaluation appends a new version linked to its predecessor and reason.
- [x] AC-06: Concurrent evaluations use an explicit expected version and cannot silently overwrite one another.
- [x] AC-07: Execution, Evidence, and Exceptions remain unchanged by a decision.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-10` | `MERGED` | Merge `a7d7548` is the branch base and current `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Outcome and eligibility tests
- Accepted-exception test
- Case-creation test
- Decision versioning and concurrency tests
- Immutable-source-data test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` under the approved decision 14 contract.
- Commits: `NONE`; implementation and verification are complete in the working tree.
- Checks: focused H2 `6/6`; focused PostgreSQL 16/Flyway V1–V22/Hibernate `6/6`;
  PostgreSQL migration gate `12/12`; complete H2 regression `126` discovered, `114` executed,
  `12` PostgreSQL-only migration tests skipped, and `0` failures. Tests cover outcome eligibility,
  accepted exceptions, incomplete/stale reviews, role/site isolation, immutable source facts,
  append-only reevaluation, stale case versions, and concurrent first decisions.
- Migration: `V22__add_compliance_cases_and_results.sql`, validated from a fresh PostgreSQL schema
  with Hibernate `validate`; adds cases, immutable result versions, criteria, findings, constraints,
  and supporting indexes without rewriting prior data.
- Blockers: `NONE_LOCAL`
- Next action: Review the diff and create the requested Conventional Commit before push/PR.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
