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

## Operator attention flow

The assigned operator submits details and one to five JPEG/PNG photographs atomically. The
`command` JSON part contains `expectedVersion`, `details`, and one `capturedAt` value per file.

```http
POST /api/v1/corrective-actions/{actionId}/attention-submissions
Authorization: Bearer <operator-access-token>
Idempotency-Key: attention-0001
Content-Type: multipart/form-data

command = {
  "expectedVersion": 0,
  "details": "The damaged seal was replaced",
  "capturedAt": ["2031-03-05T15:00:00Z"]
}
files = <proof.png>
```

The first successful request returns `201`; an exact replay during the seven-day retention returns
`200` with the original submission. A changed payload with the same key returns
`IDEMPOTENCY_KEY_MISMATCH`. The evidence is private, immutable, and never stored as Execution
Evidence.

The assigned operator or scoped supervisor can list immutable attempts with:

```http
GET /api/v1/corrective-actions/{actionId}/attention-submissions
```

Each evidence response contains its authorized `contentPath`. The supervisor returns or verifies
the current attention using a mandatory reason and expected version:

```http
POST /api/v1/corrective-actions/{actionId}/return
POST /api/v1/corrective-actions/{actionId}/verify
```

Return moves the action to `RETURNED`; a resubmission creates the next numbered immutable attempt.
Verification moves it to terminal `CLOSED`. Neither operation changes the Compliance Result.

## Lifecycle ownership

BE-US-12 owns the action and transition rules. BE-US-21 owns attention submissions, corrective
evidence, idempotent replay, return, and verification HTTP commands.
