# BE-US-15 — Generate a Compliance Report

- Type: `feature`
- Parent story: `US-15`
- Bounded context: `Reporting & Follow-up`
- Context type: `Supporting`
- Sprint: `UNASSIGNED`
- Status: `DRAFT`
- Assignee: `UNASSIGNED`
- Estimate: `13` points (planning baseline; refine with delivery team)

## Objective

Supervisors generate a reproducible, authorized compliance report whose totals reconcile with source detail.

## Acceptance criteria

- [ ] AC-01: The report consolidates obligations, evaluated results, Exceptions, non-compliances, and Corrective Actions using canonical source definitions.
- [ ] AC-02: Unevaluated Executions appear as pending and are never counted as compliant.
- [ ] AC-03: Totals and detail rows use the identical tenant, scope, service, site, period, cutoff, and status filters.
- [ ] AC-04: Service, site, period, historical cutoff, and tenant/scope authorization are mandatory.
- [ ] AC-05: The same historical cutoff and source versions reproduce the same report content.
- [ ] AC-06: Large reports use a bounded asynchronous or streaming contract with authorized status, failure, expiry, and download behavior.
- [ ] AC-07: Export format, content type, timezone, limits, and performance target are documented in OpenAPI.

## Dependencies

| Ticket or contract | Required state | Evidence |
| --- | --- | --- |
| `BE-US-09` | `MERGED` | Pending verification on `develop` |
| `BE-US-11` | `MERGED` | Pending verification on `develop` |
| `BE-US-12` | `MERGED` | Pending verification on `develop` |
| `BE-US-14` | `MERGED` | Pending verification on `develop` |

Cross-cutting contracts in [the draft contract pack](../contracts.md) require approval wherever applicable.

## Verification

- Totals/detail reconciliation test
- Pending-case test
- Historical-cutoff reproducibility
- Authorization tests
- Large-volume performance and failure tests
- Applicable backend Definition of Done in [the backlog index](../README.md).

## Progress evidence

- Status: `DRAFT`
- Commits: `NONE`
- Checks: `NOT_RUN`
- Blockers: `CONTRACTS_PENDING_APPROVAL`
- Next action: Validate scope and contracts against current `develop`, then classify readiness.
- Source: Bundled `$opervia` backend ticket catalog; this is a planning baseline, not implementation evidence.
