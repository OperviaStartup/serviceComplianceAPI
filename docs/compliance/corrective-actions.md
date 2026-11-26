# Corrective actions

BE-US-12 adds corrective work to Compliance Management without changing the Compliance Result that
motivated it. A scoped supervisor may create more than one action for a case while its latest result
is `NON_COMPLIANT`. The assignee must be an active operator in the same company and site.

## Supervisor happy path

1. Authenticate as `SUPERVISOR` and retain the access token.
2. Obtain the `caseId` from the compliance decision flow.
3. Create the action:

```http
POST /api/v1/compliance-cases/{caseId}/corrective-actions
Authorization: Bearer <supervisor-access-token>
Content-Type: application/json

{
  "assigneeId": 7,
  "description": "Replace the damaged safety seal",
  "targetDate": "2031-03-05"
}
```

The response is `201 Created`, starts in `ASSIGNED`, uses `America/Lima`, has version `0`, and
contains its creation event. The target date is inclusive: the derived status becomes `OVERDUE` at
00:00 Lima on the next day while the action is `ASSIGNED` or `RETURNED`.

4. The supervisor or assigned operator can read the action:

```http
GET /api/v1/corrective-actions/{actionId}
Authorization: Bearer <access-token>
```

The response includes persisted state, derived status, optimistic version, audit data, and ordered
immutable lifecycle events. An operator cannot read an action assigned to someone else.

## Lifecycle boundary

The domain supports `ASSIGNED -> ATTENTION_SUBMITTED`, supervisor return to `RETURNED`, resubmission,
and supervisor verification to terminal `CLOSED`. Each command checks an expected version. The
public submit, return, verification, and corrective-evidence endpoints belong to BE-US-21; BE-US-12
does not expose that unfinished contract.
