# Consulta de estado operacional

`GET /api/v1/operational-obligations` proyecta el trabajo de la empresa y sede del supervisor
autenticado. Reporting & Follow-up consume hechos de Service Planning y Field Operations sin
modificar sus agregados.

## Autorización y filtros

Solo una cuenta `SUPERVISOR` activa puede consultar. Empresa y sede provienen del servidor; enviar
otro `siteId` devuelve `403`.

Filtros opcionales:

- `serviceId` y `operatorId` positivos;
- `siteId`, que solo puede coincidir con la sede autenticada;
- `fromDate` y `toDate`, inclusivos en `America/Lima`;
- `status` repetible: `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED`, `EXCEPTED`, `OVERDUE`;
- `cursor` opaco y `limit` entre 1 y 100.

El período selecciona la fecha de inicio operacional; para registros históricos sin inicio usa
`dueAt`. Las obligaciones sin plan fuente aparecen sin filtro de servicio y se excluyen cuando se
solicita `serviceId`.

## Estado canónico

La precedencia es:

```text
EXCEPTED > SUBMITTED > OVERDUE > IN_PROGRESS > ASSIGNED
```

Una excepción registrada conserva `EXCEPTED` aunque luego se envíe la ejecución. `OVERDUE`
comienza cuando `asOf >= dueAt`, el final exclusivo de la ventana. No es una decisión de
compliance.

## Respuesta y paginación

```json
{
  "items": [
    {
      "obligationId": 42,
      "serviceId": 5,
      "siteId": 1,
      "operatorId": 18,
      "activity": "Limpieza de almacén",
      "startsAt": "2030-01-02T13:00:00Z",
      "dueAt": "2030-01-02T15:00:00Z",
      "timezone": "America/Lima",
      "status": "OVERDUE"
    }
  ],
  "counts": {
    "EXCEPTED": 0,
    "SUBMITTED": 0,
    "OVERDUE": 1,
    "IN_PROGRESS": 0,
    "ASSIGNED": 0
  },
  "total": 1,
  "nextCursor": null,
  "hasMore": false,
  "asOf": "2030-01-02T15:00:00Z"
}
```

Los conteos aplican exactamente los mismos filtros y estados solicitados que `items`. La primera
página fija `asOf`; el cursor conserva ese instante y está ligado al actor y filtros. El orden es
inicio operacional e ID de obligación.
