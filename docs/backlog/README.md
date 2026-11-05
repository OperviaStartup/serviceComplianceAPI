# Backend backlog

This is the versioned planning baseline for the Opervia Service Compliance API. The 22 `BE-US-*` records were transcribed from the bundled `$opervia` backend catalog. They are drafts until the team accepts their contracts and verifies repository evidence. US-18 and US-19 concern the landing page; US-24 has no source story. The 162-point normalized estimate is a planning baseline, not an approved commitment.

## Evidence and status

Each ticket records its own acceptance criteria, dependencies, verification, commits, checks, blockers, and next action. `DRAFT` means the ticket has not been approved. Classify readiness independently as `READY`, `PARTIAL`, `BLOCKED`, or `INSUFFICIENT` against current `develop`. A documented dependency is available only after its implementation is merged and verified there. A PR, local branch, mock, or historic claim does not establish integration.

The [contract pack](contracts.md) is pending product approval. Record approved decisions as versioned contracts before implementing affected behavior. Preserve the original story and ticket IDs; assign real sprints only when the team chooses them.

## Dependency order

| Gate | Tickets | Required predecessors |
| --- | --- | --- |
| Secure foundation | 17, 20 | 20 after 17 |
| Planning | 01, 02, 03 | 01 after 17; then 02, 03 |
| Generation | 25 | 03 and 20 |
| Field operation | 04, 05, 06, 07, 08 | 04/05 after 25; 06 after 02/05; 07 after 05; 08 after 05/06/07 |
| Compliance | 09, 10, 11 | 09 after 25/05/07; 10 after 03/05/06/07; 11 after 10 |
| Correctives and history | 12, 21, 13, 14 | Follow each ticket's dependency table |
| Follow-up | 16, 23, 22, 15 | Follow each ticket's dependency table |

For exact edges and criteria, use the ticket records. Independently ready tickets may be developed concurrently, with at most three ticket agents and one integration owner. Each agent uses an isolated worktree from current `develop` and a `feature/<ticket>-<description>` branch. Work on a downstream ticket starts only after its prerequisite is merged and its integration checks pass. `BE-US-04` and `BE-US-05` may proceed together after `BE-US-25`; `BE-US-13` and `BE-US-21` may proceed together after their individual dependencies.

## Delivery loop

1. Recheck repository instructions, Git state, the exact ticket, contracts, implementation, migrations, and tests. Map every acceptance criterion to an implementation slice and verification.
2. Implement one coherent slice with focused tests; review security, data isolation, concurrency, API compatibility, audit data, and failure recovery. Use immutable Flyway migrations and preserve the modular monolith.
3. Run focused Gradle checks, inspect the diff, and create focused Conventional Commits referencing the ticket. Push the branch and open a PR against `develop` with criteria, tests, API/security/data impact, and rollback notes.
4. The integration owner reviews the PR and CI. Record the merged commit and verify on `develop` before marking the ticket `DONE` or unlocking dependents. A new change invalidates affected validation evidence.
5. Release only after all tickets meet applicable Definition of Done and the end-to-end release gate. Merging PRs, releasing to `main`, and deployment require explicit approval.

## Definition of Done and release gate

For each ticket, assess applicable migrations; tenant, role, scope, and ownership checks; consistent 400/401/403/404/409 errors; actor and server timestamps; OpenAPI; unit, PostgreSQL, authorization, and acceptance tests; cross-company/cross-user isolation; safe logs/metrics; and existing-client compatibility or a migration path. Explain non-applicable items in its PR.

Backend completion also requires the PostgreSQL end-to-end journey, OpenAPI and authorization matrices, migration checks on empty and upgraded schemas, rehearsed backup/recovery/rollback, and monitoring for authentication failures, authorization denials, sync conflicts, alert/report failures, and latency.

## Current repository baseline (2026-11-05)

`main` at `a70a67b` has basic authentication, obligation, execution, and evidence endpoints, one Flyway migration, H2 tests with Flyway disabled, and no accepted project tickets or cross-cutting contracts. These APIs are partial foundations, not acceptance evidence for the normalized tickets. The local test attempt could not start because the host has Java 25 while `build.gradle` requires Java 17. The Gradle wrapper script is not executable in this checkout; `bash gradlew` starts it.
