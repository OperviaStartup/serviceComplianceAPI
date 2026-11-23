# BE-US-10 — Review Execution, Evidence, and Exceptions

- Type: `feature`
- Parent story: `US-10`
- Bounded context: `Compliance Management`
- Context type: `Core Domain`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `5` points (planning baseline; refine with delivery team)

## Objective

A supervisor receives one authorized, consistent review model containing all relevant execution facts.

## Acceptance criteria

- [x] AC-01: An authorized scoped supervisor can retrieve the versioned obligation definition, Execution, Evidence, Exceptions, actors, and timestamps together.
- [x] AC-02: Missing required information is identified explicitly against the applicable requirement version.
- [x] AC-03: Missing information never creates an automatic Compliance Result.
- [x] AC-04: The response uses the immutable plan version retained when the obligation was generated.
- [x] AC-05: Out-of-scope cases, evidence references, storage metadata, and download operations are inaccessible.
- [x] AC-06: The review model is a documented consistent snapshot or versioned response under concurrent updates.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-03` | `MERGED` | Merge `43e97ce` is an ancestor of current `develop` |
| `BE-US-05` | `MERGED` | Merge `b9191de` is an ancestor of current `develop` |
| `BE-US-06` | `MERGED` | Merge `26b2e55` is an ancestor of current `develop` |
| `BE-US-07` | `MERGED` | Merge `b1484e7` is an ancestor of current `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Complete and incomplete review fixtures
- Historical-version test
- Authorization tests
- Concurrent read/snapshot test
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`; readiness: `READY` under the approved decision 13 contract.
- Commits: `88d5868` (implementation); verification evidence is recorded by the branch closeout commit.
- Checks: focused Java 17 H2 `4/4`; PostgreSQL 16/Flyway V1–V21/Hibernate `4/4`; complete H2
  regression `119` discovered, `108` executed, `11` PostgreSQL-only migration tests skipped, and
  `0` failures. Role, site, company, historical-definition, missing-information, late-write, and
  private-storage-key cases are covered.
- Migration: `NOT_APPLICABLE`; the ticket adds a read projection over immutable existing facts and
  creates no table, column, index, or persisted aggregate.
- Blockers: `NONE_LOCAL`
- Next action: Review and commit the branch, then observe the expanded compliance-review CI matrix.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
