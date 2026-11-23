# Registro de resultados de cumplimiento

`POST /api/v1/compliance-reviews/executions/{executionId}/results` permite que un supervisor
con alcance sobre la empresa y sede convierta una revisión completa en una decisión auditable.
El comando crea un único Compliance Case por obligación en la primera evaluación y agrega
versiones inmutables en las reevaluaciones.

## Flujo exitoso del supervisor

1. Autenticarse como `SUPERVISOR` y obtener el access token.
2. Consultar `GET /api/v1/compliance-reviews/executions/{executionId}`.
3. Confirmar que `complete` sea `true` y conservar `reviewVersion`.
4. Para la primera decisión enviar `expectedCaseVersion: 0`. Para una reevaluación enviar el
   `caseVersion` de la última respuesta y un `reevaluationReason`.
5. Conservar `caseId`, `resultId` y `caseVersion` devueltos para seguimiento y concurrencia.

Ejemplo de una primera decisión con excepción aceptada:

```http
POST /api/v1/compliance-reviews/executions/42/results
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "outcome": "EXCEPTION_ACCEPTED",
  "reviewVersion": "execution-42-v5",
  "expectedCaseVersion": 0,
  "criteria": [
    {
      "code": "SERVICE_QUALITY",
      "status": "PASS",
      "notes": "El umbral aplicable fue alcanzado"
    }
  ],
  "findings": [
    {
      "code": "EXCEPTION_REVIEW",
      "description": "El impedimento está sustentado por la evidencia enviada",
      "exceptionId": 9,
      "exceptionDisposition": "ACCEPTED"
    }
  ],
  "explanation": "La excepción operativa fue justificada",
  "reevaluationReason": null
}
```

## Reglas de decisión

- `COMPLIANT` exige una revisión completa, cero excepciones y todos los criterios en `PASS`.
- `EXCEPTION_ACCEPTED` exige por lo menos una excepción, una decisión `ACCEPTED` para cada
  excepción y ningún criterio en `FAIL`.
- `NON_COMPLIANT` exige por lo menos un criterio en `FAIL` o una excepción en `REJECTED`. Si la
  revisión contiene excepciones, todas deben recibir una decisión explícita.
- Los códigos de criterios no se repiten. Una excepción solo puede aparecer en un hallazgo y debe
  pertenecer a la revisión indicada.
- Una revisión incompleta devuelve `409 REVIEW_INCOMPLETE`; nunca produce incumplimiento
  automático.

## Versionado, concurrencia e inmutabilidad

El servidor bloquea la obligación durante el comando. La primera confirmación crea el caso con
versión 1. Si `expectedCaseVersion` no coincide, responde `409 VERSION_CONFLICT` con
`currentVersion`; el cliente debe volver a leer antes de decidir si reintenta.

Una reevaluación agrega un resultado con `predecessorResultId` y requiere una razón. Nunca edita
el resultado anterior. Cada resultado retiene `sourceReviewVersion`, la versión de la ejecución,
el plan y la definición aplicables, criterios, hallazgos, explicación, actor y hora del servidor.
El comando no modifica Execution, Evidence ni Exceptions.
