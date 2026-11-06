# Detalle de obligación para la primera entrega Android

Este documento describe el primer incremento y sus verificaciones. Su continuación se documenta
en el [contrato del flujo del operario](operator-flow.md), que es la referencia para inicio,
avance, fotos, impedimentos, envío y vencimiento.

El primer incremento preparó los datos que necesita la pantalla de detalle del operario. Mantiene la
creación directa existente, sin activar planes ni generar obligaciones desde Service Planning.
Por sí solo no implementaba inicio/envío, avance, fotos, impedimentos ni vencimiento. Esas
capacidades se agregan en la continuación. La sincronización offline y la generación de
obligaciones de planes siguen fuera de esta entrega; no se declara US-25 completa.

## Alcance y permisos

- `POST /api/v1/obligations`: los roles existentes `SUPERVISOR` y `ADMIN` pueden crear una
  obligación y asignarla a un `OPERATOR` activo de su misma empresa y centro.
- `GET /api/v1/obligations/{id}`: el operario solo consulta su obligación asignada;
  supervisor y administrador consultan obligaciones de su misma empresa y centro.
- Empresa y centro se resuelven desde la cuenta autenticada. `siteName` es un texto de
  presentación; no permite elegir ni cambiar el alcance autorizado.
- No se modifica la configuración de roles, login, invitaciones ni permisos de Service Planning.
  La creación directa es el alcance específico acordado para esta entrega, no una implementación
  de la activación, generación o historial de asignaciones de planes.

Todas las operaciones requieren el JWT existente en `Authorization: Bearer <token>`.

## Crear una obligación con detalle de ejecución

`POST /api/v1/obligations` conserva los cinco campos existentes. Agrega `startsAt`, `steps` y
`photoRequirements`, que deben enviarse juntos si se configura el detalle de ejecución:

```json
{
  "title": "Limpiar recepción",
  "description": "Realizar la limpieza de recepción siguiendo los pasos indicados.",
  "siteName": "Recepción principal",
  "assignedOperatorId": 42,
  "startsAt": "2030-01-01T15:00:00Z",
  "dueAt": "2030-01-01T15:30:00Z",
  "steps": [
    { "title": "Piso", "instructions": "Limpiar y dejar seco." },
    { "title": "Mostrador", "instructions": "Limpiar la superficie." },
    { "title": "Área de espera", "instructions": "Ordenar las sillas." }
  ],
  "photoRequirements": [
    { "title": "Piso terminado", "description": "Mostrar el piso limpio y seco." },
    { "title": "Mostrador limpio", "description": "Mostrar la superficie del mostrador." }
  ]
}
```

Los identificadores y datos del ejemplo son ilustrativos. Deben sustituirse por datos reales de la
API. El ejemplo representa tres pasos y dos fotografías; no existe una foto por paso.

Reglas del contrato:

- `startsAt` y `dueAt` son instantes ISO-8601 con zona/desplazamiento; `dueAt > startsAt`.
  La zona de presentación acordada es `America/Lima` (el ejemplo equivale a 10:00–10:30).
- `steps` contiene al menos un paso. Su `title` es obligatorio, de hasta 255 caracteres;
  `instructions` es opcional, de hasta 2000 caracteres.
- `photoRequirements` debe indicarse explícitamente. Una lista vacía significa que no se exigen
  fotos. Cada elemento tiene `title` obligatorio, de hasta 255 caracteres, y `description`
  opcional, de hasta 2000 caracteres. Cada requisito identifica una única foto exigida; el
  registro y la unicidad de esa foto se implementarán en el bloque de evidencias.
- No se aceptan elementos nulos ni títulos vacíos. Los títulos internos se recortan; las
  descripciones/instrucciones opcionales vacías se normalizan a `null`.
- El orden del array define `position` desde 1. Los IDs y posiciones los asigna el servidor.
  El cliente no suministra IDs de pasos o requisitos ni los reutiliza entre obligaciones.
- Los pasos son instrucciones de la obligación, no tareas con ejecución independiente.
  Este bloque no incorpora un campo que indique que ya fueron realizados.

El resultado es `201 Created` con el mismo contrato de detalle que el GET, incluidos los IDs de
obligación, pasos y requisitos ya persistidos. El estado inicial sigue siendo `ASSIGNED`.

## Consultar detalle

`GET /api/v1/obligations/{id}` devuelve:

| Campo | Significado |
| --- | --- |
| `id`, `companyId`, `siteId`, `assignedOperatorId` | Identificadores de la obligación y su alcance. |
| `title`, `description`, `siteName` | Datos de presentación existentes. |
| `status`, `dueAt` | Estado y fecha límite existentes; este bloque no cambia sus reglas. |
| `startsAt` | Inicio programado o `null` en obligaciones antiguas/no configuradas. |
| `timezone` | `America/Lima`. |
| `steps` | Lista ordenada de `{ id, title, instructions, position }`. |
| `photoRequirements` | Lista ordenada de `{ id, title, description, position }`. |

Un requisito fotográfico no es una evidencia subida. Ni el POST ni el GET devuelven archivos,
URLs de almacenamiento o confirmaciones de subida. No se exige una relación con una definición
de plan para esta creación directa; no se alteran ni consultan repositorios de Service Planning.

## Compatibilidad

- La solicitud antigua, sin ninguno de los tres campos nuevos, sigue siendo válida. El detalle
  resultante tiene `startsAt: null`, `steps: []` y `photoRequirements: []`. No se inventa su
  configuración ni debe interpretarse como una obligación preparada para el futuro inicio.
- Al enviar parte de la configuración nueva, se devuelve `400` con los campos faltantes.
- El POST conserva todos sus campos anteriores y agrega los del detalle en su respuesta.
- `GET /api/v1/obligations` conserva su respuesta anterior: array de objetos con `id`,
  `companyId`, `siteId`, `title`, `description`, `siteName`, `assignedOperatorId`, `status`,
  `dueAt`. No incorpora las colecciones del detalle ni requiere cambios en el listado Kotlin.
- La app del operario abrirá el detalle usando el ID que le entregue el listado del compañero.
  El contenido se obtiene por GET; no se codifican actividades, pasos o cantidades de fotos
  dentro de las pantallas.

## Errores

Se mantiene `ApiError`: `code`, `message`, `timestamp` y, cuando corresponde, `details`.

| HTTP | Situación |
| --- | --- |
| `400` | Datos inválidos, ventana invertida/vacía o configuración incompleta. |
| `401` | Sesión ausente, inválida, suspendida o vencida. |
| `403` | Creación sin rol autorizado, responsable no elegible o detalle de otro operario del mismo centro. |
| `404` | Detalle inexistente o fuera de empresa/centro. No se exponen sus datos. |

## Migración y verificación

`V10__add_field_obligation_details.sql` agrega una fecha inicial nullable y las tablas
`obligation_steps` y `obligation_photo_requirements`, con claves foráneas, orden positivo y orden
único por obligación. V1–V9 no se modifican y los registros existentes permanecen intactos.

La migración no debe ejecutarse en la base compartida antes de identificar su responsable,
estado de migraciones y procedimiento de respaldo. El almacenamiento de fotos queda pendiente
de identificar/configurar; este bloque no crea infraestructura ni conexiones a servicios externos.

Pruebas locales, usando Java 17:

```cmd
gradlew.bat test --tests com.api.servicecompliance.ObligationDetailsIntegrationTests
gradlew.bat build
```

La configuración de pruebas predeterminada usa H2 y deshabilita Flyway. Pasar esa suite no
demuestra por sí solo que la migración funcione en PostgreSQL. La verificación de V10 debe
considerar una base PostgreSQL aislada vacía y una actualización desde V9 con datos de prueba.
La comprobación visual de Kotlin se realizará posteriormente con Run de Android Studio.

### Resultado local del incremento (2026-11-05)

- Java 17 y Gradle Wrapper 9.7.1: `gradlew.bat build --no-daemon` terminó correctamente.
  La suite predeterminada ejecutó 45 pruebas sin fallos y omitió las dos pruebas PostgreSQL opt-in.
- PostgreSQL 18.4 local, en un clúster temporal exclusivo de pruebas: pasaron las 10 pruebas de
  `ObligationDetailsIntegrationTests` con Flyway habilitado y Hibernate en modo `validate`, y las
  dos de `ObligationDetailsMigrationTests`. Se verificaron creación desde cero, actualización
  desde V9 conservando obligación/ejecución/evidencia y restricciones de integridad.
- El servidor temporal se detuvo al terminar. No se verificó PostgreSQL 16 ni la instancia compartida.
- Se compararon los contenidos con la copia anterior al cambio: no hay modificaciones a
  autenticación, ejecuciones, Service Planning, V1–V9 ni Kotlin. No se ejecutaron comandos Git.

Las pruebas de migración se habilitan únicamente con una URL local del siguiente formato. La base
debe existir en un servidor exclusivo de pruebas; cada prueba crea un esquema nuevo independiente.
No se debe sustituir por la conexión del grupo. Ejemplo CMD para ese entorno local ya preparado:

```cmd
set "FIELD_OPERATIONS_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55439/field_operations_test"
set "FIELD_OPERATIONS_TEST_DB_USER=field_ops_test"
gradlew.bat test --no-daemon --tests com.api.servicecompliance.ObligationDetailsMigrationTests
```

Si el servidor local exige contraseña, se proporciona mediante
`FIELD_OPERATIONS_TEST_DB_PASSWORD`, fuera del código y sin publicarla. Las pruebas no se
habilitan con URLs remotas o nombres de base distintos de `field_operations_test`.

## Archivos del incremento

- `obligations/domain/model/Obligation.java`: horario y composición del detalle.
- `obligations/domain/model/ObligationStep.java`: instrucción ordenada con identidad propia.
- `obligations/domain/model/ObligationPhotoRequirement.java`: fotografía exigida identificable.
- `obligations/domain/repository/ObligationRepository.java`: consulta delimitada por empresa/sede.
- `obligations/application/ObligationService.java`: validación, persistencia y respuestas explícitas.
- `obligations/interfaces/rest/ObligationController.java`: contrato ampliado y GET de detalle.
- `src/main/resources/db/migration/V10__add_field_obligation_details.sql`.
- `src/test/java/com/api/servicecompliance/ObligationDetailsIntegrationTests.java`.
- `src/test/java/com/api/servicecompliance/ObligationDetailsMigrationTests.java`.
- Este documento y su enlace en `README.md`.

Las rutas `obligations/` parten de `src/main/java/com/api/servicecompliance/`. El informe académico
y sus diagramas deberán incorporar estas tablas y el alcance de creación directa cuando el grupo
documente la entrega. No se modifican esos artefactos desde este incremento del backend.
