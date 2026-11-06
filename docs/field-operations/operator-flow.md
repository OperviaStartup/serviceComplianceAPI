# Flujo del operario: contrato de la primera entrega

Este documento complementa el [detalle de obligación](obligation-detail.md). Implementa el flujo
online acordado: detalle, inicio, recuperación del avance, fotografías, impedimentos y envío.
Mantiene un centro por cuenta y los permisos existentes. La activación/generación de planes,
las pantallas de login/listado y US-08 no forman parte de este incremento.

## Integración con el trabajo del compañero

La pantalla recibe el ID de la obligación seleccionada y utiliza la sesión existente. No se
modifican las rutas, solicitudes ni implementación del login. El código y formato del listado
`GET /api/v1/obligations` se conservan. Los estados de sus filas son datos que actualiza este
módulo al iniciar, enviar o vencer una obligación.

La cuenta autenticada determina empresa, centro, rol y responsabilidad; el cliente no elige esos
valores. Las escrituras nuevas son exclusivas del operario asignado. Supervisor y administrador
pueden leer dentro de su empresa/centro, sin ejecutar trabajo en nombre del operario.

## Rutas

Todas llevan prefijo `/api/v1` y usan `Authorization: Bearer <token>`.

| Método y ruta | Solicitud | Respuesta |
| --- | --- | --- |
| POST `/obligations/{id}/execution` | Sin cuerpo. | `201` al iniciar; `200` al repetir, con la misma ejecución. |
| GET `/obligations/{id}/execution` | Sin cuerpo. | `200`, ejecución actual con todo su avance; `404` si no ha iniciado. |
| GET `/executions/{id}/flow` | Sin cuerpo. | La misma vista completa por ID de ejecución. |
| PUT `/executions/{id}/steps/{stepId}` | `{ completed, expectedVersion }` | Vista completa con el paso actualizado. |
| POST `/executions/{id}/exceptions` | Motivo, descripción, operación y versión; paso opcional. | Vista completa con el impedimento conservado. |
| POST `/executions/{id}/photo-requirements/{requirementId}/photo` | Multipart: `file`, `expectedVersion`; `capturedAt` opcional. | Vista completa tras almacenar realmente el archivo. |
| POST `/executions/{id}/submit` | `{ expectedVersion, notes? }` | Vista enviada, validada por el servidor. |
| GET `/evidence/{id}/content` | Sin cuerpo. | Bytes JPEG/PNG privados, con autorización y `Cache-Control: no-store`. |

Los IDs del listado, del detalle y de cada respuesta se reutilizan en la siguiente operación.
No deben sustituirse por IDs fijos ni datos de demostración en las pantallas.

## Vista de ejecución

Incluye `id`, `obligationId`, `operatorId`, `state`, `obligationStatus`, `result`,
`startedAt`, `submittedAt`, `startsAt`, `dueAt`, `timezone`, `version`, `canEdit`,
`notes`, `steps`, `photoRequirements` y `exceptions`.

- Cada paso conserva su ID, título, instrucciones, posición, `completed` y `updatedAt`.
- Cada requisito fotográfico conserva su ID, título, descripción y posición; agrega
  `evidenceId`, `uploadedAt` y `contentPath` solamente cuando existe un archivo registrado.
- Cada impedimento incluye ID, paso opcional, motivo, descripción, actor, fecha y `operationId`.
- Nunca se devuelve la clave del almacenamiento ni su ruta interna. `contentPath` es una ruta
  protegida de nuestra API, no una URL pública del proveedor.
- En curso, `result` es `null`. El operario no selecciona ni envía un estado de resultado.

## Estados y tiempo

| Estado de obligación | Significado para la app |
| --- | --- |
| ASSIGNED | Asignada, sin iniciar. |
| IN_PROGRESS | En curso; el progreso puede ser parcial. |
| SUBMITTED | Ejecución completada y enviada; evaluación de cumplimiento todavía no determinada. |
| OVERDUE | Vencida; edición bloqueada. |

La ejecución nueva tiene estados `IN_PROGRESS` y `SUBMITTED`, separados del estado de la
obligación y de cualquier Compliance Result. `LEGACY` identifica exclusivamente registros del
contrato anterior. No hay un estado manual `PARTIAL` en el flujo nuevo: se muestra el avance
mientras sigue en curso.

La ventana es `[startsAt, dueAt)`: incluye el inicio y excluye exactamente la hora límite.
La autoridad es el reloj del servidor; las fechas son instantes y la presentación es
`America/Lima`. Una obligación enviada a tiempo no vence posteriormente.

Las consultas del detalle/flujo derivan el vencimiento inmediatamente. Un proceso periódico
actualiza también el estado persistido de las obligaciones configuradas pendientes/en curso,
sin modificar el código del listado. El intervalo predeterminado es 30 segundos, configurable
mediante `app.field-operations.expiry-delay-ms`; no determina el permiso de edición. Cada
comando comprueba el límite exacto por su cuenta. `app.field-operations.expiry-enabled=false`
deshabilita solamente ese proceso periódico, como ocurre en las pruebas con reloj controlado.

El servidor valida y registra cada acción usando el mismo instante. Una subida que termina fuera
del plazo no se confirma como evidencia y se intenta eliminar su objeto privado al revertir la
transacción. No se reabre una ejecución ni se concede una ampliación de plazo automáticamente.

## Inicio, progreso y concurrencia

- Se crea una única ejecución por obligación configurada. Los reintentos devuelven su identidad
  y progreso existentes; no reinician ni duplican los pasos.
- Los pasos pueden marcarse y desmarcarse mientras la ejecución esté abierta y dentro del plazo.
- `version` es la versión persistida de la ejecución. Toda escritura usa `expectedVersion`
  de la última vista recibida, nunca una constante.
- Un cambio desactualizado devuelve `409 VERSION_CONFLICT` y `currentVersion`. Se vuelve a
  consultar antes de decidir repetirlo. No hay sobrescritura silenciosa.
- Las operaciones se serializan mediante un bloqueo de la obligación y mantienen la versión
  optimista de la ejecución. Una restricción PostgreSQL impide duplicar ejecuciones nuevas.
- Repetir un paso que ya tiene ese valor devuelve la vista actual sin otra modificación.

## Impedimentos: US-07

Ejemplo ilustrativo; los IDs, la versión y UUID deben proceder del flujo real:

```json
{
  "reason": "AREA_CLOSED",
  "description": "La puerta de acceso está cerrada con llave.",
  "stepId": 123,
  "operationId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "expectedVersion": 2
}
```

Los motivos permitidos corresponden a los mockups: `AREA_CLOSED`, `MISSING_SUPPLIES`,
`SAFETY_RISK`, `OTHER`. La descripción es obligatoria, de hasta 2000 caracteres.
Sin `stepId`, el impedimento corresponde a la obligación completa; si se indica, debe ser
un paso de esa obligación.

`operationId` es un UUID estable para el reintento del mismo registro, limitado por ejecución
y, por sus permisos, por operario/empresa. El mismo contenido normalizado devuelve el
impedimento original; un cambio de contenido con el mismo UUID devuelve
`IDEMPOTENCY_KEY_MISMATCH`. La versión no forma parte de ese contenido semántico.

El impedimento no completa la obligación, no exime fotografías o pasos, no bloquea la posibilidad
de continuar antes del límite y no genera una evaluación de cumplimiento. Permanece en el
historial aunque el operario consiga después completar y enviar la ejecución.

## Fotografías: US-06

- Cada requisito admite una única fotografía. Un nuevo archivo diferente no puede reemplazarla.
- Un reintento con los mismos bytes conserva el registro original, sus metadatos y fecha; no
  vuelve a subir el archivo. Los reintentos de una operación ya realizada no reabren la ejecución.
- Se admiten JPEG y PNG reales, de hasta 5 MiB (5 × 1024 × 1024 bytes). Se comprueban el contenido,
  el tipo declarado y que se pueda decodificar. Como límite técnico de memoria, no se admiten
  imágenes de más de 25 millones de píxeles.
- `capturedAt` es opcional y representa la fecha declarada por el dispositivo, no una fecha
  verificada de captura. No puede ser futura respecto al servidor. `uploadedAt` es independiente
  y lo registra el servidor.
- El requisito debe pertenecer a la obligación ejecutada. Tener una URL o enviar un ID ajeno
  no satisface ningún requisito.
- Si falla el proveedor o la base de datos, no se confirma el registro como exitoso. Se compensa
  un rollback eliminando el objeto nuevo. Ante un estado transaccional desconocido se conserva
  el archivo para reconciliación en lugar de arriesgar eliminar una evidencia comprometida.
- La descarga comprueba nuevamente los permisos y el SHA-256 almacenado. No expone el archivo si
  su integridad no coincide. Las respuestas del proveedor tienen límite de tamaño y tiempo.
- Una obligación configurada explícitamente sin fotografías no exige fotos; sigue exigiendo sus pasos.

### Configuración privada

El adaptador usa Supabase Storage sin sustituir nuestra API, autenticación ni la base PostgreSQL
que ya tiene el grupo. La cuenta, bucket y credenciales reales aún deben identificarse/configurarse.

| Variable de entorno del backend | Valor esperado |
| --- | --- |
| FIELD_PHOTO_STORAGE_URL | URL HTTPS del proyecto, sin sufijo `/storage/v1`. |
| FIELD_PHOTO_STORAGE_SERVICE_KEY | Clave `service_role` de servidor; nunca incluirla en Android ni en Git. |
| FIELD_PHOTO_STORAGE_BUCKET | Nombre del bucket existente y privado. |

El código no crea una base nueva, buckets ni permisos externos. Antes de subir o descargar
comprueba que el bucket no sea público. Sin configuración válida o con el proveedor inaccesible
devuelve `503 PHOTO_STORAGE_UNAVAILABLE`; no guarda una evidencia ficticia. HTTP sin TLS se
admite únicamente contra `127.0.0.1` para las pruebas del adaptador.

La integración sigue los contratos oficiales de
[upload](https://supabase.com/docs/reference/self-hosting-storage/upload-a-new-object),
[descarga privada](https://supabase.com/docs/guides/storage/serving/downloads) y
[buckets privados](https://supabase.com/docs/guides/storage/buckets/fundamentals).
Los endpoints privados usan exclusivamente credenciales de servidor y no siguen redirecciones.

## Envío explícito

`POST /executions/{id}/submit` exige todos los pasos marcados, todas las fotos exigidas
registradas, versión vigente y plazo abierto. `notes` es opcional y admite hasta 2000 caracteres.
No recibe un resultado elegido por el operario.

Solo al superar esas validaciones registra `submittedAt`, establece `result=COMPLETED` y
cambia ejecución y obligación a `SUBMITTED` en la misma transacción. A partir de entonces
no se pueden modificar los pasos, fotografías o impedimentos. Repetir el mismo envío devuelve
el registro enviado; cambiar sus notas después del envío devuelve conflicto.

`COMPLETED` expresa que se completó este registro operativo. No demuestra limpieza física
automática, validez jurídica, protocolo verificado, nitidez, ni aprobación del supervisor.

## Compatibilidad y errores

Los registros anteriores se conservan como `LEGACY`, sin inventar fechas de inicio/envío.
Los endpoints antiguos continúan disponibles para sus obligaciones antiguas no configuradas.
En las configuradas se rechaza el POST antiguo de resultado con
`EXECUTION_LIFECYCLE_REQUIRED`, y en las ejecuciones nuevas se rechaza el POST de URLs con
`PHOTO_UPLOAD_REQUIRED`. Así no pueden usarse para saltarse pasos/fotos.

Si una obligación tiene registros históricos, el flujo nuevo devuelve `LEGACY_EXECUTION`;
no crea otra ejecución ni reinterpreta un historial ambiguo. Para esta entrega se preparan
obligaciones nuevas con su configuración explícita.

Se conserva `ApiError` y los códigos 400/401/403/404. Los conflictos 409 incluyen:
`EXECUTION_NOT_CONFIGURED`, `EXECUTION_NOT_STARTED_YET`, `OBLIGATION_OVERDUE`,
`INVALID_EXECUTION_STATE`, `EXECUTION_ALREADY_SUBMITTED`, `VERSION_CONFLICT`,
`PHOTO_ALREADY_ATTACHED`, `IDEMPOTENCY_KEY_MISMATCH` y los de compatibilidad anteriores.
Una ejecución incompleta devuelve `400 VALIDATION_ERROR` con `details.steps` y/o
`details.photoRequirements`. Parámetros multipart ausentes o mal formados devuelven
`400 INVALID_REQUEST`; una carga que supera el límite del servidor devuelve
`413 PHOTO_TOO_LARGE`. Almacenamiento ausente, inválido o con contenido corrupto devuelve 503.

## Persistencia y verificación

V11 conserva los datos históricos y agrega ciclo/versión a `executions`,
`execution_step_progress`, `operational_exceptions` y metadatos privados en `evidences`.
No cambia V1–V10, usuarios, pertenencias a centros ni las tablas de Service Planning.

```mermaid
erDiagram
    OBLIGATIONS ||--o{ EXECUTIONS : registra
    OBLIGATIONS ||--o{ OBLIGATION_STEPS : define
    OBLIGATIONS ||--o{ OBLIGATION_PHOTO_REQUIREMENTS : exige
    EXECUTIONS ||--o{ EXECUTION_STEP_PROGRESS : conserva
    EXECUTIONS ||--o{ OPERATIONAL_EXCEPTIONS : documenta
    EXECUTIONS ||--o{ EVIDENCES : respalda
    OBLIGATION_STEPS ||--o{ EXECUTION_STEP_PROGRESS : identifica
    OBLIGATION_PHOTO_REQUIREMENTS ||--o{ EVIDENCES : satisface
```

La multiplicidad histórica de ejecuciones se conserva; el índice parcial de V11 limita a una
única ejecución nueva por obligación. El registro de impedimentos no tiene ninguna relación
de creación automática con Compliance Results.

Comprobaciones locales con Java 17:

```cmd
gradlew.bat build --no-daemon
gradlew.bat test --no-daemon --tests com.api.servicecompliance.OperatorFlowIntegrationTests --tests com.api.servicecompliance.SupabasePhotoStorageTests
```

Las pruebas del flujo usan reloj controlado y almacenamiento en memoria **solo en pruebas**.
Las del adaptador usan un servidor HTTP local. Ninguna de esas pruebas demuestra una conexión
real a Supabase. No hay almacenamiento ficticio como alternativa de producción.

Las suites de migración PostgreSQL son opt-in para una base temporal local exclusiva:
`FIELD_OPERATIONS_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55439/field_operations_test`,
usuario mediante `FIELD_OPERATIONS_TEST_DB_USER` y contraseña, si existe, mediante
`FIELD_OPERATIONS_TEST_DB_PASSWORD`. Crean esquemas separados y rechazan una URL remota.
Se deben comprobar instalación vacía, actualización con historial, integridad, acceso y concurrencia.

Antes de aplicar V10/V11 a la instancia compartida, el grupo debe identificarla y comprobar
migraciones, respaldo y configuración. La puesta en marcha real de fotos requiere el bucket
privado configurado. Kotlin se validará posteriormente con Run de Android Studio; US-08 sigue
pendiente. El informe académico deberá reflejar este alcance y las nuevas relaciones.

### Resultado de verificación local (2026-11-05)

- Java 17 y Gradle Wrapper 9.7.1: `gradlew.bat build --no-daemon` correcto, con 69 pruebas
  ejecutadas sin fallos. Cuatro pruebas de migración quedan omitidas en esa ejecución por
  requerir el PostgreSQL local opt-in.
- PostgreSQL 18.4 temporal y exclusivo de pruebas: 38 pruebas seleccionadas sin fallos,
  con Flyway habilitado y Hibernate en modo `validate`. Incluyen detalle, flujo completo,
  concurrencia, fechas límite, preservación de datos de V9/V10, restricciones V10/V11 y el
  adaptador HTTP local. No se probó una instancia PostgreSQL 16 ni el despliegue compartido.
- El servidor temporal se detuvo al terminar. No se creó ni modificó infraestructura externa.
- La comparación de contenidos confirmó intactos autenticación, Service Planning, controlador
  de obligaciones, método y respuesta del listado, dependencias y migraciones V1–V10. El
  servicio de detalle solo agrega el reloj para reflejar vencimiento inmediatamente; no cambia
  el listado. No se ejecutaron comandos Git.
- Las credenciales y el bucket real no estuvieron disponibles: la prueba contra Supabase real
  y la aplicación coordinada de V10/V11 en la base del grupo siguen pendientes. El adaptador
  está implementado y comprobado contra su contrato HTTP local, no contra una cuenta real.

Comando PostgreSQL ejecutado, con las variables anteriores apuntando exclusivamente al clúster
local y las variables de Spring Datasource/Flyway dirigidas a esa misma base temporal:

```cmd
gradlew.bat test --no-daemon --rerun-tasks --tests com.api.servicecompliance.OperatorFlowIntegrationTests --tests com.api.servicecompliance.OperatorFlowMigrationTests --tests com.api.servicecompliance.ObligationDetailsIntegrationTests --tests com.api.servicecompliance.ObligationDetailsMigrationTests --tests com.api.servicecompliance.SupabasePhotoStorageTests
```

## Archivos de esta continuación

Rutas relativas al backend; los paquetes Java parten de
`src/main/java/com/api/servicecompliance/`:

- `executions/application/FieldExecutionService.java`: ciclo, permisos, avance, envío y consistencia.
- `executions/application/ExecutionService.java`: impide saltar el flujo nuevo por el POST antiguo.
- `executions/application/EvidenceService.java`: impide sustituir la subida por una URL declarada.
- `executions/application/PhotoStorage.java`, `PhotoStorageUnavailableException.java`, `PhotoValidator.java`.
- `executions/domain/model/Execution.java`, `ExecutionState.java`, `ExecutionStepProgress.java`.
- `executions/domain/model/Evidence.java`, `OperationalException.java`, `ExceptionReason.java`.
- `executions/domain/repository/ExecutionRepository.java`, `EvidenceRepository.java`,
  `ExecutionStepProgressRepository.java`, `OperationalExceptionRepository.java`.
- `executions/infrastructure/FieldOperationsConfiguration.java`, `ObligationExpiryJob.java`,
  `SupabasePhotoStorage.java`.
- `executions/interfaces/rest/FieldOperationController.java`: endpoints separados del login/listado.
- `obligations/domain/model/Obligation.java`, `obligations/domain/repository/ObligationRepository.java`.
- `obligations/application/ObligationService.java`: reloj únicamente para el detalle/creación.
- `shared/web/GlobalExceptionHandler.java`: errores controlados de multipart y almacenamiento.
- `src/main/resources/application.properties`: configuración privada y límites multipart.
- `src/main/resources/db/migration/V11__add_operator_execution_flow.sql`.
- `src/test/java/com/api/servicecompliance/OperatorFlowIntegrationTests.java`,
  `OperatorFlowMigrationTests.java`, `SupabasePhotoStorageTests.java`.
- `src/test/java/com/api/servicecompliance/ObligationDetailsMigrationTests.java`: fija la prueba
  original de V10 a esa versión para conservar su propósito al agregar V11.
- `README.md`, `docs/backlog/contracts.md`, `docs/field-operations/obligation-detail.md`
  y este documento.
