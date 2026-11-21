# Sincronización offline del operario

Este contrato permite que la aplicación Kotlin conserve trabajo de campo sin conexión y lo
reproduzca sin duplicar efectos. Complementa el [flujo online del operario](operator-flow.md) y
reutiliza sus reglas de permisos, versiones, evidencia y transiciones.

## Endpoint y límites

`POST /api/v1/sync/batches` requiere un JWT de una cuenta `OPERATOR` activa y
`Content-Type: multipart/form-data`.

- Parte `manifest`: JSON con un `batchId` UUID y entre 1 y 50 operaciones.
- Partes `files`: hasta 10 archivos. El `originalFilename` de cada parte debe coincidir exactamente
  con el `fileKey` de una operación `ATTACH_EVIDENCE`.
- Tamaño total de archivos: 25 MiB. Cada archivo también conserva los límites de su tipo de
  evidencia.
- Un envelope inválido devuelve `400`. Un envelope válido devuelve `200`, aunque algunos items
  requieran reintento o atención.

## Identidad, orden y recuperación

Cada item lleva un `clientOperationId` UUID estable, único para la empresa y el operario. El
servidor retiene resultados terminales durante 30 días. Repetir el mismo ID y contenido devuelve
el mismo resultado; reutilizarlo con contenido diferente devuelve
`REQUIRES_ATTENTION / IDEMPOTENCY_KEY_MISMATCH`.

`dependsOn` declara dependencias generales. `executionRef` referencia el `clientOperationId` de
un `START_EXECUTION`, y `evidenceRefs` referencia items `ATTACH_EVIDENCE`. El servidor ordena el
grafo; el orden del array no necesita coincidir con el orden de ejecución. La respuesta sí conserva
el orden original del manifest.

Cada item usa una transacción independiente. Si la conexión se interrumpe, se reenvía el mismo
manifest: los items ya confirmados se reproducen y los pendientes se ejecutan. Los resultados son:

- `SYNCED`: efecto confirmado y resultado terminal guardado.
- `RETRYABLE`: error transitorio; debe repetirse con el mismo `clientOperationId`.
- `REQUIRES_ATTENTION`: conflicto de negocio o datos que necesita intervención. Tras corregir el
  contenido, se crea un nuevo `clientOperationId`, porque el resultado anterior es terminal.

El `batchId` sirve para correlación del cliente; la recuperación depende de cada item. Ninguno de
estos resultados cambia por sí mismo el estado de compliance.

## Operaciones

| `type` | Campos principales |
| --- | --- |
| `START_EXECUTION` | `obligationId` |
| `UPDATE_STEP` | `executionId` o `executionRef`, `stepId`, `completed`, `expectedVersion` |
| `ATTACH_EVIDENCE` | ejecución, `evidenceRequirementId`, `capturedAt`, `expectedVersion`; `fileKey` para PHOTO/SIGNATURE o valores estructurados para QR_CODE/LOCATION |
| `REPORT_EXCEPTION` | ejecución, `reason`, `description`, `expectedVersion`; opcionalmente `stepId`, `evidenceIds` y `evidenceRefs` |
| `SUBMIT_EXECUTION` | ejecución, `expectedVersion`, `notes` opcional |

`executionId` y `executionRef` son excluyentes. Las escrituras con versión obsoleta devuelven
`REQUIRES_ATTENTION / VERSION_CONFLICT` y `currentVersion`.

## Ejemplo multipart

El ejemplo envía las operaciones deliberadamente desordenadas. Kotlin puede construir el
manifest con kotlinx.serialization y las partes con el cliente HTTP elegido.

```bash
curl -X POST 'http://localhost:8080/api/v1/sync/batches' \
  -H 'Authorization: Bearer <JWT>' \
  -F 'manifest={
    "batchId":"11111111-1111-4111-8111-111111111111",
    "items":[
      {
        "clientOperationId":"33333333-3333-4333-8333-333333333333",
        "type":"ATTACH_EVIDENCE",
        "executionRef":"22222222-2222-4222-8222-222222222222",
        "dependsOn":["22222222-2222-4222-8222-222222222222"],
        "evidenceRequirementId":51,
        "fileKey":"photo-1.jpg",
        "capturedAt":"2026-11-21T15:00:00Z",
        "expectedVersion":0
      },
      {
        "clientOperationId":"22222222-2222-4222-8222-222222222222",
        "type":"START_EXECUTION",
        "obligationId":20
      }
    ]
  };type=application/json' \
  -F 'files=@./photo.jpg;filename=photo-1.jpg;type=image/jpeg'
```

Respuesta resumida:

```json
{
  "batchId": "11111111-1111-4111-8111-111111111111",
  "items": [
    {
      "clientOperationId": "33333333-3333-4333-8333-333333333333",
      "outcome": "SYNCED",
      "code": "SYNCED",
      "resourceType": "EVIDENCE",
      "resourceId": 90,
      "currentVersion": 1
    },
    {
      "clientOperationId": "22222222-2222-4222-8222-222222222222",
      "outcome": "SYNCED",
      "code": "SYNCED",
      "resourceType": "EXECUTION",
      "resourceId": 70,
      "currentVersion": 0
    }
  ]
}
```
