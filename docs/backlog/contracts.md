# Backend product contracts — draft for approval

> Field Operations, primera entrega local: las decisiones específicas aprobadas para el flujo
> online se documentan en [operator-flow.md](../field-operations/operator-flow.md). Mantienen los
> permisos existentes, un centro por operario, creación directa sin activar planes, pasos con
> avance, una foto inmutable por requisito, impedimentos separados, envío explícito completo y
> vencimiento. Ese alcance sustituye los defaults aún draft de ciclo/estado cuando difieren;
> no aprueba ni implementa automáticamente los tickets de planificación, compliance u offline.

Status: **PARTIALLY APPROVED**. Decisions 1–5 were approved for `BE-US-17` and `BE-US-20`.
Decisions 6 and 8 were approved for `BE-US-01`; the definition-only portions of decisions 7 and 10
were approved for `BE-US-02` on 2026-11-05. The BE-US-05 portions of decisions 8 and 9 were
approved on 2026-11-15. Decision 10 was fully approved for `BE-US-06` on 2026-11-16. Unapproved
portions of decisions 7, 9, and 11–18 remain draft. Preserve
`/api/v1` compatibility or document a consumer migration path.

## Proposed defaults for review

The table below is the proposed baseline. The scoped approvals and refinements recorded in the
sections below control where they differ.

| # | Proposed decision |
| --- | --- |
| 1 | Three roles: company ADMIN manages plans/users, scoped SUPERVISOR assigns/reviews, assigned OPERATOR performs work. Company is the tenant boundary; site membership is the initial organizational scope. Resolve every resource through tenant and permitted site before reading protected data. |
| 2 | Extend `ApiError` compatibly with optional `details` and `currentVersion`; use stable machine-readable 409 codes for idempotency mismatch, version conflict, overlap, and invalid state. Mutations accept an explicit expected version where concurrency matters. |
| 3 | Use 30-minute access JWTs and 7-day rotating opaque refresh tokens stored as hashes; resolve the active account on every protected request. Logout revokes the refresh family; suspension revokes all active sessions. |
| 4 | Generate 256-bit random single-use invitation tokens, store only SHA-256 hashes, deliver 72-hour activation links through Resend, and return the existing accepted account on an exact replay. New accounts use an eight-digit Peruvian DNI as login identifier and set their password during activation; passwords are never emailed. Existing-account conflicts return `ACCOUNT_ALREADY_EXISTS`. |
| 5 | Stripe sandbox subscriptions project a local entitlement: `PLUS` has 3 seats and `CUSTOM` uses the purchased Stripe quantity. Active accounts and live pending invitations consume seats; suspension, invitation expiry, or cancellation releases one. Serialize allocation on the company subscription so the first committed request receives the final seat and the loser receives `SEAT_LIMIT_REACHED`. Reactivation must acquire a seat again. |
| 6 | Store plan periods as inclusive local dates `[start, end]` in `America/Lima`. Drafts may overlap; activation rejects overlap for the same company/service/site and different plan series. An amendment clones the active plan into a new draft business version; activating it supersedes, but never rewrites, the prior version. |
| 7 | Definitions use structured `ONCE`, `DAILY`, or `WEEKLY` schedules with recurrence interval, optional weekdays/occurrence date, local start time, execution-window duration, and `America/Lima`. Occurrence generation and recovery remain owned by `BE-US-25`. |
| 8 | Scope operation IDs to company, actor, command, and client key. Persist a SHA-256 hash of normalized semantic fields and the created-resource response reference for seven days. Exact replay returns the original result; a changed payload returns `IDEMPOTENCY_KEY_MISMATCH`. A unique database constraint serializes concurrent use of the same key. |
| 9 | Server transitions `NOT_STARTED → IN_PROGRESS → SUBMITTED` or `EXCEPTED`, with explicit cancellation/retry policy. Sync and compliance states use separate fields/enums. |
| 10 | `BE-US-02` defines `PHOTO`, `QR_CODE`, `LOCATION`, and `SIGNATURE` requirements with required/optional status, count bounds, and allowlisted metadata. Generated obligations retain immutable requirement snapshots. `BE-US-06` stores PHOTO/SIGNATURE privately and QR/LOCATION structurally, requires seven-day idempotency, enforces count/metadata rules, and retains immutable evidence with its execution. |
| 11 | Accept bounded batches with client operation IDs and dependency references; process in dependency order with per-item transactions and stable results `SYNCED`, `RETRYABLE`, `REQUIRES_ATTENTION`. Use version conflicts for stale writes. |
| 12 | Derive operational status from execution lifecycle and window with precedence `EXCEPTED`, `SUBMITTED`, `IN_PROGRESS`, `OVERDUE`, `ASSIGNED`; overdue starts at exclusive window end. Reuse the same predicate for rows and counts. |
| 13 | Freeze the submitted evidence/exception set by version; review reads one immutable submitted version, and later additions require a new explicit revision. |
| 14 | Create one case per obligation when first evaluated. Append immutable decision versions with source review and plan versions, expected case version, and reason for reevaluation; no automatic non-compliance. |
| 15 | Allow multiple corrective actions per eligible non-compliant case, each with one active eligible assignee. State transitions include assigned, attention submitted, returned, and supervisor verified/closed; overdue derives from target boundary. |
| 16 | Persist append-only typed events with immutable event ID, schema version, actor and server timestamp; order and cursor by `(timestamp, eventId)` to resolve ties. |
| 17 | Persist recipient-specific alert occurrences keyed by rule, resource, occurrence and recipient; initially deliver in-app, with a recoverable outbox for later channels. Re-evaluate current access before exposing details. |
| 18 | Produce a version/cutoff-bound CSV report through a bounded asynchronous job; authorize job status and download by tenant/scope, set expiry, and record source versions for reproducibility. |

The remaining schedule syntax, content allowlist, report performance target, and later API schemas remain open values. They must be fixed in accepted contracts before their affected tickets become `READY`.

## Identity and security

1. **Role and scope matrix.** Define administrator, supervisor, operator, company, organizational scope hierarchy, and ownership checks for every command and query. Specify who may administer users and sites, and whether supervisors can act across sites. A tenant identifier must be authoritative server-side. Affected: all tickets; first gate: `BE-US-17`.
2. **Error and concurrency contract.** Approve the `ApiError` fields, field-level validation shape, 409 reason codes, expected-version transport, and retry guidance. Existing `ApiError(code,message,timestamp)` lacks a 409/version contract. Affected: all write tickets; first gate: `BE-US-17`/`BE-US-01`.
3. **Session policy.** Decide access/refresh lifetimes, refresh rotation, logout, revocation, disabled-account behavior, and whether token claims or fresh account lookup control access. Current JWT contains email subject and role, without refresh or company identity. Affected: `BE-US-17`, `BE-US-20`.
4. **Invitation policy.** Approved for `BE-US-20`: 256-bit token, SHA-256 storage, Resend delivery, 72-hour expiry, single use, idempotent accepted replay, DNI login, password creation during activation, and explicit existing-account conflict.
5. **Seat policy.** Approved for `BE-US-20`: local Stripe entitlement projection, Plus 3 seats, Custom purchased quantity, active plus live pending consumption, suspension/expiry/cancellation release, serialized final-seat allocation, and seat reacquisition on reactivation.

## Planning and field work

6. **Plan periods.** Approved for `BE-US-01`: inclusive `LocalDate` boundaries, server-authoritative
   `America/Lima`, overlapping drafts, activation-time overlap rejection for the same
   company/service/site, and immutable successor versions for amendments. Affected: `BE-US-01`,
   `02`, `03`.

   Approved activation/version refinement for `BE-US-03`: each row is one immutable business
   version in a stable series. Activation is idempotent, requires an expected persistence version,
   and records actor/time. Amendment clones the active version and definitions into the next draft;
   successor activation atomically marks the predecessor `SUPERSEDED`. A company-service database
   lock serializes concurrent overlap checks.
7. **Schedules.** Approved for `BE-US-02`: structured `ONCE`, `DAILY`, and `WEEKLY` definitions,
   inclusive plan-date validation, local time/window, and `America/Lima`. For `BE-US-25`, generation
   is an explicit bounded command with `fromDate` and `toDate`, limited to the active plan period;
   local occurrence identity is `(definitionId, occurrenceDate)`, reruns skip existing occurrences,
   and schedule evaluation uses the definition timezone. Generated obligations retain the plan
   business version and definition version. Affected: `BE-US-02`, `03`, `25`.
8. **Idempotency.** Approved initial contract for `BE-US-01`: require `Idempotency-Key`, scope it
   by company, actor, and command, retain it for seven days, hash normalized semantic fields,
   replay the original resource for the same payload, and return
   `IDEMPOTENCY_KEY_MISMATCH` for a changed payload. A database uniqueness constraint decides
   concurrent duplicates. Later affected tickets reuse this baseline unless their offline command
   contract requires an explicitly approved extension. Affected: `BE-US-01`, `05`, `06`, `07`,
   `08`, `21`.

   Approved `BE-US-05` refinement: start and submit require an `Idempotency-Key` of 8–100
   approved characters. Scope is company, actor, and command for seven days. Start hashes the
   obligation ID; submit hashes the execution ID and normalized notes; `expectedVersion` is not
   semantic payload. Exact replay returns the same execution, a changed payload returns
   `IDEMPOTENCY_KEY_MISMATCH`, and database uniqueness plus obligation locking prevents duplicate
   business effects. The legacy `/api/v1/executions` contract remains unchanged.
9. **Execution lifecycle.** Approved for `BE-US-05`: an assigned obligation without an Execution
   is `NOT_STARTED`; persisted online Executions transition only `IN_PROGRESS → SUBMITTED` under
   server authority. Submission records `COMPLETED` as the operational result, not a compliance
   decision. Overdue, unassigned, reassigned, legacy, or already-submitted resources reject invalid
   commands. V1 has no cancellation, reopening, or second execution attempt. Reporting an Exception
   remains separate and leaves the Execution `IN_PROGRESS`; sync and compliance use separate state.
   Affected: `BE-US-05`, `07`, `08`, `09`.
10. **Evidence.** Approved definition contract for `BE-US-02`: reuse the stable evidence codes
    `PHOTO`, `QR_CODE`, `LOCATION`, and `SIGNATURE`; record required/optional status, minimum and
    maximum count, and allowlisted type-specific validation metadata. Approved `BE-US-06`
    refinement: each generated obligation stores an immutable copy linked to the source requirement
    and definition version. PHOTO accepts decoded JPEG/PNG up to 5 MiB and 25 million pixels;
    SIGNATURE accepts PNG up to 1 MiB. QR_CODE stores at most 512 characters and validates
    `expectedPrefix`; LOCATION stores latitude, longitude, accuracy, and capture time and validates
    `maxAccuracyMeters`. PHOTO may require geolocation and enforce `maxAgeMinutes`.

    Binary objects remain private in Supabase and are served only through the authorized backend;
    QR and location payloads remain in PostgreSQL. Every attachment requires an `Idempotency-Key`
    scoped to company, actor, and command for seven days. Exact replay returns the original
    evidence, while changed semantic content returns `IDEMPOTENCY_KEY_MISMATCH`. Evidence is
    immutable, maximum counts are enforced, and submission checks only required minimum counts;
    optional evidence never blocks submission. Records retain actor, capture/server timestamps,
    type, size, checksum, and structured metadata. V1 performs no automatic evidence deletion or
    replacement; evidence follows execution/case retention. Downloads reauthorize company, site,
    execution, and owner without exposing storage keys. The legacy photo endpoint remains available
    for pre-existing configured obligations. Affected: `BE-US-02`, `06`, `10`, `21`.
11. **Offline sync.** Set batch limits, client references/dependency order, transaction boundaries, per-item outcomes, conflict and retry codes, and interruption recovery. Affected: `BE-US-08`, `23`.

## Review, follow-up, and reporting

12. **Operational status.** Define ASSIGNED/IN_PROGRESS/SUBMITTED/EXCEPTED/OVERDUE precedence, overdue boundary/timezone, and stable filter/count semantics. Affected: `BE-US-04`, `09`, `15`, `16`.

    Approved `BE-US-04` refinement: preserve the legacy `GET /api/v1/obligations` array and add
    `/api/v1/assigned-obligations` for the operator sync contract. Snapshot filters use inclusive
    `America/Lima` dates and the approved online statuses `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED`,
    and `OVERDUE`; overdue begins at the exclusive execution-window end. Pages use an opaque,
    filter-bound keyset ordered by operational start and obligation ID, with a maximum size of 100.
    A separate opaque change cursor returns ordered `UPSERT` and `REMOVED` changes; reassignment
    records removal for the former operator and upsert for the new operator. Company, site, and
    current operator come only from the authenticated account.
13. **Review snapshot.** Decide whether evidence freezes on submission or review returns a versioned snapshot under concurrent changes. Affected: `BE-US-10`, `11`.
14. **Compliance Case.** Define case creation, eligible outcomes, decision criteria, reevaluation reason, append-only versions, and concurrent evaluation. Affected: `BE-US-11`, `13`, `14`.
15. **Corrective actions.** Define eligibility, multiplicity, eligible assignees, transition matrix, verification, and target-date rules. Affected: `BE-US-12`, `21`.
16. **Timeline.** Define event ownership, type/payload evolution, stable ID, cursor, and equal-timestamp tie-breaker. Affected: `BE-US-14`.
17. **Alerts.** Define recipient resolution, channels, rule configuration, occurrence identity, deduplication, delivery retry, and escalation. Affected: `BE-US-16`, `23`.
18. **Reports.** Define export format, cutoff/timezone, consistent snapshot, asynchronous job retention, download authorization, size limit, and performance target. Affected: `BE-US-15`.

## Session implementation

Access JWTs expire after 30 minutes by default and are checked against the active account and its current role, scope, and session version. Refresh tokens are random opaque 256-bit values; only SHA-256 hashes are stored. Each successful refresh rotates the token with a seven-day lifetime. Reuse revokes the entire family, as does logout; previously issued access JWTs remain usable until expiration after logout. The `RefreshService.suspend` transaction disables the account, increments its session version, and revokes every refresh family; the future BE-US-20 suspension command must call this capability rather than update `active` directly. Existing JWTs without a session version require a new login after deployment.

## BE-US-17 authorization matrix

| Surface | ADMIN | SUPERVISOR | OPERATOR | Scope and ownership |
| --- | --- | --- | --- | --- |
| Login, refresh, logout | Allowed | Allowed | Allowed | Account must be active; refresh token must be current, unexpired, and not revoked. |
| Current profile | Allowed | Allowed | Allowed | Only the authenticated account. |
| Create obligation | Allowed | Allowed | Denied | Assignee must be an active operator in the actor's company and site. |
| List assigned obligations | Own assignments | Own assignments | Own assignments | Query is restricted by authenticated user, company, and site. |
| Register execution or evidence | Denied | Denied | Allowed | Only the current assigned operator in the obligation's company and site. |
| Read execution or evidence | Allowed | Allowed | Allowed for owner | ADMIN/SUPERVISOR require matching company and site; OPERATOR must also own the execution. |

Executions and evidence derive their tenant and site boundary from the owning obligation. The legacy migration assigns every existing user and obligation to company `1` and site `1`; execution and evidence links remain unchanged.

## Registration migration

**BREAKING CHANGE:** Public `POST /api/v1/auth/register` is disabled and returns 403 without creating an account. Existing clients must stop calling it and transition to invitation acceptance when BE-US-20 is implemented; no invitation flow is provided by BE-US-17.

## Approval record

- Decision owner: User direction in this conversation.
- Approval date and revision: 2026-11-05 through 2026-11-16; decisions 1–6, the BE-US-01 and
  BE-US-05 portions of decision 8, and the BE-US-05 portion of decision 9,
  including 30-minute access JWTs, seven-day rotating refresh tokens, Resend invitations, DNI
  login, Stripe-backed seat rules, inclusive Lima plan dates, seven-day plan-creation
  idempotency, the BE-US-04 assigned-work synchronization refinement of decision 12, and the
  approved BE-US-05 online execution lifecycle, and the complete BE-US-06 evidence contract.
- Approved references: User approval for `BE-US-17`, `BE-US-20`, `BE-US-01`, `BE-US-05`, and
  `BE-US-06`; decision 7 and the remaining portions of decisions 9 and 11–18 remain pending.
- First implementation gate: `BE-US-17` is merged into `develop`; `BE-US-20` may proceed on `feature/be-us-20-user-management` against the approved local Stripe entitlement contract.
