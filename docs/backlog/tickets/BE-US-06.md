# BE-US-06 — Attach required Evidence

- Type: `feature`
- Parent story: `US-06`
- Bounded context: `Field Operations`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `IN_REVIEW`
- Assignee: `Codex`
- Estimate: `8` points (planning baseline; refine with delivery team)

## Objective

Evidence is stored securely and validated against the exact versioned requirement and attachment context.

## Acceptance criteria

- [x] AC-01: The assigned operator can attach Evidence to that operator's Execution under the documented upload protocol.
- [x] AC-02: Evidence records target context, type, storage reference or payload metadata, capture timestamp, actor, checksum/integrity metadata, and audit timestamps.
- [x] AC-03: Type, size, format, metadata, and required status are validated against the applicable versioned Evidence Requirement.
- [x] AC-04: Requirements from another obligation, definition version, company, or attachment context are never applied.
- [x] AC-05: Submission is blocked only for genuinely required missing evidence; optional or absent requirements do not block it.
- [x] AC-06: Operation replay does not duplicate evidence, and unauthorized or guessed downloads return no evidence or sensitive metadata.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-02` | `MERGED` | Merge `1cf89d0` and passing regression suite on this branch |
| `BE-US-05` | `MERGED` | Merge `b9191de` and passing regression suite on this branch |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Required/optional evidence tests
- Format and limit validation
- Resource download authorization
- Duplicate upload test
- Missing-evidence submission tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `IN_REVIEW`
- Commits: `4d760b2` implementation and verification.
- Checks: Java 17 full H2 suite: 103 tests, 95 executed and 8 opt-in skips; PostgreSQL 16 HTTP/Flyway/Hibernate suite: 25/25; focused V1–V17 migration suite: 5/5. Real Supabase integration not executed because no test project credentials were used.
- Blockers: `NONE_FOR_LOCAL_REVIEW`; merge and verification on `develop` remain required for `DONE`.
- Next action: Review the diff, create focused Conventional Commits, and hand off for push/PR.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
