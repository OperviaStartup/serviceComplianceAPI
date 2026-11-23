# Revisión consistente de una ejecución

`GET /api/v1/compliance-reviews/executions/{executionId}` entrega al supervisor un modelo de
revisión autorizado y autocontenido. Compliance Management consume los snapshots de planificación
y los hechos enviados por Field Operations; esta consulta no crea ni modifica un Compliance Result.

## Autorización y estado

- Solo una cuenta `SUPERVISOR` activa puede consultar.
- Empresa y sede provienen de la cuenta autenticada.
- Una ejecución inexistente o fuera de ese alcance devuelve `404` sin revelar cuál condición falló.
- La ejecución debe estar `SUBMITTED`; de lo contrario devuelve `409 REVIEW_NOT_READY` con
  `currentVersion`.

## Consistencia y versiones

La respuesta se construye en una transacción `REPEATABLE_READ`. Después del envío, los comandos
existentes rechazan cambios de pasos, evidencia y excepciones, por lo que el conjunto revisado queda
congelado. `reviewVersion` identifica la versión de ejecución enviada.

El bloque `obligation` contiene `sourcePlanId`, `sourcePlanVersion`, `sourceDefinitionId` y
`sourceDefinitionVersion`, además de la copia de pasos y requisitos retenida al generar la
obligación. Nunca vuelve a leer la definición vigente para reinterpretar trabajo histórico.

## Contenido

La respuesta incluye:

- obligación y definición aplicables;
- ejecución, operador, timestamps y versión;
- avance de cada paso;
- requisitos y cantidades de evidencia;
- evidencia estructurada o binaria con actor e integridad;
- excepciones y sus referencias de evidencia;
- `complete` y `missingInformation` calculados contra el snapshot aplicable.

`missingInformation` puede señalar `STEP_NOT_COMPLETED`, `PHOTO_REQUIRED` o
`MINIMUM_EVIDENCE_NOT_MET`. Es información para revisión, no una decisión automática de
cumplimiento.

La respuesta nunca expone `storageKey`. Cuando existe contenido binario, `contentPath` apunta al
endpoint protegido `/api/v1/evidence/{id}/content`, que vuelve a autorizar empresa, sede y recurso.
