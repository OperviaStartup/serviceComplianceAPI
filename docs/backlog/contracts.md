# Backend product contracts — draft for approval

Status: **DRAFT**. These are decisions to review, not approved behavior. The normalized backlog names the subjects but does not define their wire formats or business rules. Record the accepted choices here (or in linked ADRs) and update affected ticket records before implementation. Preserve `/api/v1` compatibility or document a consumer migration path.

## Proposed defaults for review

These recommendations are working proposals; none is authorized for implementation yet.

| # | Proposed decision |
| --- | --- |
| 1 | Three roles: company ADMIN manages plans/users, scoped SUPERVISOR assigns/reviews, assigned OPERATOR performs work. Company is the tenant boundary; site membership is the initial organizational scope. Resolve every resource through tenant and permitted site before reading protected data. |
| 2 | Extend `ApiError` compatibly with optional `details` and `currentVersion`; use stable machine-readable 409 codes for idempotency mismatch, version conflict, overlap, and invalid state. Mutations accept an explicit expected version where concurrency matters. |
| 3 | Use short-lived access JWTs and rotating opaque refresh tokens stored as hashes; validate active account on protected requests. Logout revokes the refresh family; suspension revokes all active sessions. Publish exact lifetimes before implementation. |
| 4 | Generate random single-use invitation tokens, store hashes, send a link by an approved channel, expire tokens, and return the existing accepted account on an exact replay. Existing-account conflicts require an explicit admin flow. |
| 5 | Reserve a contracted seat for each active account and each live pending invitation; release on invitation expiry/cancel or account deactivation. Enforce with a serialized company-level transaction. |
| 6 | Store plan periods as `[start, end)` in the site's IANA timezone; reject active overlap for the same company/service/site. Amend by creating a new immutable version for future occurrences. |
| 7 | Use a constrained recurring schedule plus local execution window and IANA timezone. Define DST gap/fold behavior in the accepted schedule schema; persist occurrence identity and regenerate missed horizon safely. |
| 8 | Scope operation IDs to company, actor, command, and client key. Persist canonical request hash and response; exact replay returns prior result, changed payload is 409. Define retention based on offline retry duration. |
| 9 | Server transitions `NOT_STARTED → IN_PROGRESS → SUBMITTED` or `EXCEPTED`, with explicit cancellation/retry policy. Sync and compliance states use separate fields/enums. |
| 10 | Store evidence in private object storage, use server-authorized upload/download, checksum and content validation, bounded size/type allowlist, malware scan before availability, and documented retention. Keep metadata in PostgreSQL. |
| 11 | Accept bounded batches with client operation IDs and dependency references; process in dependency order with per-item transactions and stable results `SYNCED`, `RETRYABLE`, `REQUIRES_ATTENTION`. Use version conflicts for stale writes. |
| 12 | Derive operational status from execution lifecycle and window with precedence `EXCEPTED`, `SUBMITTED`, `IN_PROGRESS`, `OVERDUE`, `ASSIGNED`; overdue starts at exclusive window end. Reuse the same predicate for rows and counts. |
| 13 | Freeze the submitted evidence/exception set by version; review reads one immutable submitted version, and later additions require a new explicit revision. |
| 14 | Create one case per obligation when first evaluated. Append immutable decision versions with source review and plan versions, expected case version, and reason for reevaluation; no automatic non-compliance. |
| 15 | Allow multiple corrective actions per eligible non-compliant case, each with one active eligible assignee. State transitions include assigned, attention submitted, returned, and supervisor verified/closed; overdue derives from target boundary. |
| 16 | Persist append-only typed events with immutable event ID, schema version, actor and server timestamp; order and cursor by `(timestamp, eventId)` to resolve ties. |
| 17 | Persist recipient-specific alert occurrences keyed by rule, resource, occurrence and recipient; initially deliver in-app, with a recoverable outbox for later channels. Re-evaluate current access before exposing details. |
| 18 | Produce a version/cutoff-bound CSV report through a bounded asynchronous job; authorize job status and download by tenant/scope, set expiry, and record source versions for reproducibility. |

The exact numeric lifetimes, limits, schedule syntax, content allowlist, seat capacity source, report performance target, and API schemas remain open values. They must be fixed in accepted contracts before their affected tickets become `READY`.

## Identity and security

1. **Role and scope matrix.** Define administrator, supervisor, operator, company, organizational scope hierarchy, and ownership checks for every command and query. Specify who may administer users and sites, and whether supervisors can act across sites. A tenant identifier must be authoritative server-side. Affected: all tickets; first gate: `BE-US-17`.
2. **Error and concurrency contract.** Approve the `ApiError` fields, field-level validation shape, 409 reason codes, expected-version transport, and retry guidance. Existing `ApiError(code,message,timestamp)` lacks a 409/version contract. Affected: all write tickets; first gate: `BE-US-17`/`BE-US-01`.
3. **Session policy.** Decide access/refresh lifetimes, refresh rotation, logout, revocation, disabled-account behavior, and whether token claims or fresh account lookup control access. Current JWT contains email subject and role, without refresh or company identity. Affected: `BE-US-17`, `BE-US-20`.
4. **Invitation policy.** Decide token entropy/storage, delivery channel, expiry, one-time use, replay response, and existing-account handling. Affected: `BE-US-20`.
5. **Seat policy.** Decide whether pending invitations reserve seats and which account states count; specify behavior under simultaneous invitation acceptance/suspension. Affected: `BE-US-20`.

## Planning and field work

6. **Plan periods.** Define inclusive/exclusive boundaries, overlap for company/service/site, authoritative timezone, and amendment/version rules. Affected: `BE-US-01`, `02`, `03`.
7. **Schedules.** Select syntax, daylight-saving behavior, generation horizon, occurrence identity, and missed-run recovery. Affected: `BE-US-02`, `03`, `25`.
8. **Idempotency.** Define operation-key scope, retention, payload canonicalization/mismatch, response replay, and concurrency behavior. Affected: `BE-US-01`, `05`, `06`, `07`, `08`, `21`.
9. **Execution lifecycle.** Define start, submit, exception, cancellation/retry states and transition authority; keep execution, sync, and compliance states separate. Affected: `BE-US-05`, `07`, `08`, `09`.
10. **Evidence.** Choose storage, upload/download protocol, allowed formats/sizes, integrity, retention, scanning, and privacy behavior. Evidence requirements apply only to their version and context. Affected: `BE-US-02`, `06`, `10`, `21`.
11. **Offline sync.** Set batch limits, client references/dependency order, transaction boundaries, per-item outcomes, conflict and retry codes, and interruption recovery. Affected: `BE-US-08`, `23`.

## Review, follow-up, and reporting

12. **Operational status.** Define ASSIGNED/IN_PROGRESS/SUBMITTED/EXCEPTED/OVERDUE precedence, overdue boundary/timezone, and stable filter/count semantics. Affected: `BE-US-04`, `09`, `15`, `16`.
13. **Review snapshot.** Decide whether evidence freezes on submission or review returns a versioned snapshot under concurrent changes. Affected: `BE-US-10`, `11`.
14. **Compliance Case.** Define case creation, eligible outcomes, decision criteria, reevaluation reason, append-only versions, and concurrent evaluation. Affected: `BE-US-11`, `13`, `14`.
15. **Corrective actions.** Define eligibility, multiplicity, eligible assignees, transition matrix, verification, and target-date rules. Affected: `BE-US-12`, `21`.
16. **Timeline.** Define event ownership, type/payload evolution, stable ID, cursor, and equal-timestamp tie-breaker. Affected: `BE-US-14`.
17. **Alerts.** Define recipient resolution, channels, rule configuration, occurrence identity, deduplication, delivery retry, and escalation. Affected: `BE-US-16`, `23`.
18. **Reports.** Define export format, cutoff/timezone, consistent snapshot, asynchronous job retention, download authorization, size limit, and performance target. Affected: `BE-US-15`.

## Approval record

- Decision owner: **UNASSIGNED**
- Approval date and revision: **PENDING**
- Approved references: **NONE**
- First implementation gate: `BE-US-17` after decisions 1–3 are accepted; other tickets unlock only when their relevant decisions and merged dependencies are verified.
