# Observaciones del cliente

BE-US-13 agrega feedback posterior del cliente al `Compliance Case` sin modificar ejecuciones,
evidencias ni resultados anteriores. Todos los endpoints requieren un `SUPERVISOR` activo y fijan
empresa y sede desde la cuenta autenticada. Un recurso fuera del alcance responde `404`.

## Flujo

1. `POST /api/v1/compliance-cases/{caseId}/observations` registra descripción, origen y fecha
   observada. Los orígenes válidos son `EMAIL`, `PHONE`, `SITE_VISIT` y `OTHER`; la fecha usa
   `America/Lima` y no puede estar en el futuro.
2. `GET /api/v1/compliance-cases/{caseId}/observations` devuelve todas las revisiones en orden de
   registro. No colapsa ni reemplaza el historial.
3. `POST /api/v1/client-observations/{id}/amendments` crea un snapshot completo con
   `expectedRevision` y motivo obligatorio. Solo la última revisión de una serie puede enmendarse.
4. `POST /api/v1/client-observations/{id}/reevaluations` aplica las mismas reglas de decisión de
   BE-US-11 y agrega un nuevo `Compliance Result`. El servidor genera el motivo desde la observación
   y guarda `sourceObservationId`.

Cada revisión de una observación puede originar como máximo un resultado. Un segundo intento recibe
`OBSERVATION_ALREADY_EVALUATED`; una revisión o versión desactualizada recibe un conflicto estable.
La observación por sí sola no cambia la decisión vigente.

## Ejemplos mínimos

```json
{
  "description": "El cliente reportó una deficiencia de calidad",
  "origin": "PHONE",
  "observedDate": "2031-03-04"
}
```

```json
{
  "expectedRevision": 1,
  "description": "El cliente confirmó la deficiencia por correo",
  "origin": "EMAIL",
  "observedDate": "2031-03-04",
  "reason": "Se corrigió el origen y la redacción"
}
```

La reevaluación recibe `outcome`, `reviewVersion`, `expectedCaseVersion`, `criteria`, `findings` y
`explanation`, igual que una decisión ordinaria pero sin aceptar un motivo libre del cliente.
