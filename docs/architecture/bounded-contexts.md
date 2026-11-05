# Backend bounded contexts

This document records the backend domain boundaries approved for Opervia Service Compliance.
Every backend ticket has one owning bounded context. Cross-context behavior is integrated through
explicit application contracts; a ticket does not move domain rules or persistence ownership into
another context merely because it consumes that context's identifiers or capabilities.

## Context map

| Bounded context | Type | Responsibility | Current code boundary |
| --- | --- | --- | --- |
| Identity & Access | Generic | Identify provider-company users and authorize capabilities by role, tenant, site, account state, and contracted entitlement. | `com.api.servicecompliance.authentication` |
| Service Planning | Supporting | Convert approved operating conditions into Service Plans, Obligation Definitions, and Evidence Requirements. | Not implemented; first package will be `com.api.servicecompliance.serviceplanning`. |
| Field Operations | Supporting | Materialize and assign Service Obligations and record Execution, Evidence, Exceptions, and offline synchronization. | Partial baseline in `com.api.servicecompliance.obligations` and `com.api.servicecompliance.executions`. |
| Compliance Management | Core Domain | Evaluate Executions against applicable criteria and preserve Compliance Results, observations, Corrective Actions, and Compliance Case history. | Not implemented; first package will be `com.api.servicecompliance.compliance`. |
| Reporting & Follow-up | Supporting | Project operational and compliance state for queries, alerts, personal history, notifications, and Compliance Reports. | Not implemented; first package will be `com.api.servicecompliance.reporting`. |

`com.api.servicecompliance.shared` is a technical/shared-kernel area, not a bounded context. It may
contain stable primitives and HTTP infrastructure, but it must not become the owner of business rules.

## Ticket ownership

| Bounded context | Owning tickets |
| --- | --- |
| Identity & Access | `BE-US-17`, `BE-US-20` |
| Service Planning | `BE-US-01`, `BE-US-02`, `BE-US-03` |
| Field Operations | `BE-US-25`, `BE-US-04`, `BE-US-05`, `BE-US-06`, `BE-US-07`, `BE-US-08` |
| Compliance Management | `BE-US-10`, `BE-US-11`, `BE-US-12`, `BE-US-13`, `BE-US-14`, `BE-US-21` |
| Reporting & Follow-up | `BE-US-09`, `BE-US-15`, `BE-US-16`, `BE-US-22`, `BE-US-23` |

## Integration direction

| Consumer | Provider | Contract purpose |
| --- | --- | --- |
| Service Planning | Identity & Access | Authoritative actor, company, role, and permitted site scope. |
| Field Operations | Service Planning | Active immutable plan version, obligation definitions, schedules, and evidence requirements. |
| Field Operations | Identity & Access | Eligible assignees and current tenant/site authorization. |
| Compliance Management | Service Planning | Versioned evaluation criteria applicable to an obligation. |
| Compliance Management | Field Operations | Submitted execution, evidence, and exception facts. |
| Reporting & Follow-up | All upstream contexts | Authorized projections and events; reporting does not mutate upstream aggregates. |

## Boundary rules

1. A ticket changes domain behavior in exactly one owning bounded context.
2. New contexts receive their own top-level package with `domain`, `application`, `infrastructure`,
   and `interfaces.rest` layers when implementation begins.
3. A context owns its tables, migrations, aggregates, repositories, commands, and invariants.
4. Cross-context consumers use identifiers, immutable snapshots, application ports, or versioned
   events. They do not query another context's repository or mutate another context's entity.
5. Authentication and authorization remain centralized in Identity & Access, while every consuming
   context still enforces its own tenant, role, scope, and resource-ownership rules server-side.
6. Existing direct coupling is treated as migration debt and removed only when an owning ticket
   touches that boundary; this architecture decision does not authorize a broad rewrite.

## Approval record

- Decision owner: user direction in this delivery session.
- Recorded: 2026-11-05.
- First new context: `BE-US-01` will establish the Service Planning package after its period,
  timezone, overlap, and versioning contracts are approved.
