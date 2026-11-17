# Happy path del administrador

Este documento describe cómo un `ADMIN` prepara trabajo operativo versionado para que luego lo
ejecute un `OPERATOR`. Encadena capacidades de Identity & Access, Service Planning y Field
Operations sin mezclar sus responsabilidades. El recorrido del operario continúa en
[operator-flow.md](operator-flow.md#happy-path-completo-de-endpoints).

Stripe checkout y la administración comercial de la suscripción no forman parte de este recorrido.
La suscripción de la empresa debe estar activa y tener un asiento disponible antes de invitar a un
usuario.

## Precondiciones y valores encadenados

Todas las rutas llevan el prefijo `/api/v1`. Los valores siguientes deben proceder de la
configuración o de respuestas reales; los IDs de los ejemplos son ilustrativos:

```text
BASE_URL       = http://localhost:8080/api/v1
ADMIN_TOKEN    = respuesta de login.token
SITE_ID        = sede provisionada para la empresa y el administrador
SERVICE_ID     = servicio activo provisionado para la empresa
OPERATOR_ID    = respuesta de aceptación de invitación.userId o usuario activo conocido
PLAN_ID        = respuesta de creación de plan.id
PLAN_VERSION   = respuesta más reciente del plan.version
DEFINITION_ID  = respuesta de definición.id
OBLIGATION_ID  = generation.obligations[].id
```

La API actual no expone endpoints para listar o crear `company_services`, listar sedes ni listar
usuarios. En la base local inicial, las migraciones provisionan una empresa, sede y servicio de
limpieza, pero una aplicación no debe asumir que sus IDs son `1` fuera de ese entorno. Hasta que
existan catálogos administrativos, `SITE_ID` y `SERVICE_ID` pertenecen a la configuración del
tenant. Para un operario nuevo, `OPERATOR_ID` se obtiene en el paso de aceptación; para uno
existente debe conservarse en el estado administrativo que ya conoce la cuenta.

Para probar el flujo en la fecha local actual se puede definir:

```bash
BASE_URL='http://localhost:8080/api/v1'
PLAN_DATE=$(TZ=America/Lima date +%F)
```

La definición de ejemplo empieza a medianoche y dura 1440 minutos, por lo que la obligación
generada permanece dentro de su ventana durante todo `PLAN_DATE` en `America/Lima`.

## Resumen de rutas

| Paso | Método y ruta | Rol |
| --- | --- | --- |
| Autenticación | POST `/auth/login` | Público con credenciales ADMIN |
| Invitación | POST `/invitations` | ADMIN |
| Activación del operario | POST `/invitations/accept` | Público con token de un solo uso |
| Crear plan | POST `/service-plans` | ADMIN |
| Agregar definición | POST `/service-plans/{planId}/obligation-definitions` | ADMIN |
| Consultar plan | GET `/service-plans/{planId}` | ADMIN |
| Activar plan | POST `/service-plans/{planId}/activate` | ADMIN |
| Generar obligaciones | POST `/service-plans/{planId}/obligations/generate` | ADMIN |
| Asignar operario | PUT `/obligations/{obligationId}/assignment` | ADMIN o SUPERVISOR |
| Verificar obligación | GET `/obligations/{obligationId}` | ADMIN, SUPERVISOR u OPERATOR propietario |

## 1. Iniciar sesión como administrador

```bash
curl -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{
    "identifier": "<DNI_O_CORREO_ADMIN>",
    "password": "<CONTRASEÑA_ADMIN>"
  }'
```

Fragmento de respuesta:

```json
{
  "token": "<JWT_ACCESS_TOKEN>",
  "userId": 7,
  "role": "ADMIN",
  "refreshToken": "<TOKEN_OPACO>"
}
```

Guardar `token` como `ADMIN_TOKEN`. El `refreshToken` solo se usa con `/auth/refresh` o
`/auth/logout`; nunca se envía como Bearer.

## 2. Obtener un operario activo

Si ya existe un `OPERATOR` activo de la misma empresa y sede, conservar su ID como `OPERATOR_ID` y
continuar en el paso 3. Para incorporar uno nuevo, el administrador crea una invitación:

```bash
curl -X POST "$BASE_URL/invitations" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"fullName\": \"Operario de prueba\",
    \"email\": \"operario@example.com\",
    \"documentNumber\": \"87654321\",
    \"role\": \"OPERATOR\",
    \"siteId\": $SITE_ID
  }"
```

Respuesta `201`:

```json
{
  "id": "<INVITATION_ID>",
  "email": "operario@example.com",
  "documentNumber": "87654321",
  "role": "OPERATOR",
  "siteId": 1,
  "status": "PENDING",
  "deliveryStatus": "PENDING",
  "expiresAt": "<INSTANTE_ISO_8601>"
}
```

La respuesta no contiene el token secreto. El operario lo recibe mediante el enlace de Resend y
define su propia contraseña. Esta llamada es el único paso del recorrido que no realiza el
administrador:

```bash
curl -X POST "$BASE_URL/invitations/accept" \
  -H 'Content-Type: application/json' \
  -d '{
    "token": "<TOKEN_RECIBIDO_POR_EMAIL>",
    "password": "<CONTRASEÑA_SEGURA_DE_12_A_72_CARACTERES>"
  }'
```

Fragmento de respuesta:

```json
{
  "userId": 42,
  "email": "operario@example.com",
  "documentNumber": "87654321",
  "status": "ACTIVE"
}
```

Guardar `userId` como `OPERATOR_ID`. La invitación pendiente reserva un asiento; errores como
`SEAT_LIMIT_REACHED`, `INVITATION_ALREADY_PENDING` o `ACCOUNT_ALREADY_EXISTS` deben resolverse
antes de continuar.

## 3. Crear el plan de servicio en borrador

La fecha inicial y final son inclusivas en `America/Lima`. La creación requiere una clave
idempotente estable durante el reintento:

```bash
curl -X POST "$BASE_URL/service-plans" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Idempotency-Key: plan-550e8400-e29b-41d4-a716-446655440100' \
  -H 'Content-Type: application/json' \
  -d "{
    \"serviceId\": $SERVICE_ID,
    \"siteId\": $SITE_ID,
    \"startDate\": \"$PLAN_DATE\",
    \"endDate\": \"$PLAN_DATE\"
  }"
```

La primera solicitud y su replay exacto responden `201` con el mismo plan. Fragmento:

```json
{
  "id": 210,
  "serviceId": 1,
  "siteId": 1,
  "startDate": "<PLAN_DATE>",
  "endDate": "<PLAN_DATE>",
  "timezone": "America/Lima",
  "state": "DRAFT",
  "version": 0,
  "businessVersion": 1,
  "missingActivationRequirements": [
    "OBLIGATION_DEFINITION_REQUIRED"
  ]
}
```

Guardar `id` como `PLAN_ID`. Cambiar el contenido reutilizando la misma clave devuelve
`409 IDEMPOTENCY_KEY_MISMATCH`.

## 4. Agregar una definición operativa y sus evidencias

La definición siguiente crea una ocurrencia `ONCE` para `PLAN_DATE`, una ventana que cubre todo el
día y un requisito de cada tipo. Todos son obligatorios para que el happy path del operario pruebe
PHOTO, QR_CODE, LOCATION y SIGNATURE.

```bash
curl -X POST "$BASE_URL/service-plans/$PLAN_ID/obligation-definitions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"siteId\": $SITE_ID,
    \"activity\": \"Inspeccionar recepción\",
    \"instructions\": \"Revisar el área y registrar toda la evidencia requerida\",
    \"acceptanceCriteria\": \"Área limpia, evidencia legible y ubicación dentro de tolerancia\",
    \"recurrenceType\": \"ONCE\",
    \"occurrenceDate\": \"$PLAN_DATE\",
    \"recurrenceInterval\": 1,
    \"daysOfWeek\": [],
    \"localStartTime\": \"00:00:00\",
    \"windowDurationMinutes\": 1440,
    \"evidenceRequirements\": [
      {
        \"type\": \"PHOTO\",
        \"required\": true,
        \"minimumCount\": 1,
        \"maximumCount\": 2,
        \"constraints\": {
          \"requireGeolocation\": \"true\",
          \"maxAgeMinutes\": \"30\"
        }
      },
      {
        \"type\": \"QR_CODE\",
        \"required\": true,
        \"minimumCount\": 1,
        \"maximumCount\": 1,
        \"constraints\": {
          \"expectedPrefix\": \"OPER-\"
        }
      },
      {
        \"type\": \"LOCATION\",
        \"required\": true,
        \"minimumCount\": 1,
        \"maximumCount\": 1,
        \"constraints\": {
          \"maxAccuracyMeters\": \"20\"
        }
      },
      {
        \"type\": \"SIGNATURE\",
        \"required\": true,
        \"minimumCount\": 1,
        \"maximumCount\": 1,
        \"constraints\": {
          \"signerLabel\": \"Supervisor de turno\"
        }
      }
    ]
  }"
```

Respuesta `201`:

```json
{
  "id": 330,
  "servicePlanId": 210,
  "siteId": 1,
  "activity": "Inspeccionar recepción",
  "recurrenceType": "ONCE",
  "occurrenceDate": "<PLAN_DATE>",
  "timezone": "America/Lima",
  "version": 0,
  "evidenceRequirements": [
    {
      "id": 401,
      "type": "PHOTO",
      "required": true,
      "minimumCount": 1,
      "maximumCount": 2
    }
  ]
}
```

Guardar `id` como `DEFINITION_ID`. Si se necesita corregir la definición antes de activar, usar
`PUT /service-plans/{PLAN_ID}/obligation-definitions/{DEFINITION_ID}` con el cuerpo completo y
`expectedVersion` igual a la versión más reciente de la definición. Una vez activo el plan, la
definición deja de ser editable.

## 5. Consultar y activar el plan

Obtener la versión autoritativa del plan y comprobar que ya no falten requisitos:

```bash
curl "$BASE_URL/service-plans/$PLAN_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

La respuesta debe conservar `state: "DRAFT"`, devolver `missingActivationRequirements: []` y
proporcionar `version`. Guardarla como `PLAN_VERSION` y activar:

```bash
curl -X POST "$BASE_URL/service-plans/$PLAN_ID/activate" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"expectedVersion\": $PLAN_VERSION
  }"
```

Fragmento de respuesta:

```json
{
  "id": 210,
  "businessVersion": 1,
  "state": "ACTIVE",
  "activatedBy": 7,
  "activatedAt": "<INSTANTE_DEL_SERVIDOR>",
  "version": 1
}
```

La activación rechaza definiciones sin reglas, versiones antiguas y otro plan activo que se
superponga para la misma empresa, servicio y sede.

## 6. Generar las obligaciones del periodo

El rango debe estar dentro del periodo inclusivo del plan activo:

```bash
curl -X POST "$BASE_URL/service-plans/$PLAN_ID/obligations/generate" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"fromDate\": \"$PLAN_DATE\",
    \"toDate\": \"$PLAN_DATE\"
  }"
```

Respuesta feliz:

```json
{
  "planId": 210,
  "fromDate": "<PLAN_DATE>",
  "toDate": "<PLAN_DATE>",
  "generated": 1,
  "obligations": [
    {
      "id": 510,
      "definitionId": 330,
      "occurrenceDate": "<PLAN_DATE>",
      "startsAt": "<PLAN_DATE>T05:00:00Z",
      "dueAt": "<DÍA_SIGUIENTE>T05:00:00Z"
    }
  ]
}
```

Guardar cada `obligations[].id` como `OBLIGATION_ID`. Repetir el mismo rango es seguro: las
ocurrencias ya existentes se omiten y `generated` puede ser `0`; en ese replay la respuesta no
vuelve a listar las obligaciones previas, por lo que el cliente debe conservar los IDs de la
primera generación exitosa.

## 7. Asignar cada obligación al operario

La obligación generada nace sin operario. El destino debe estar activo, tener rol `OPERATOR` y
pertenecer a la misma empresa y sede:

```bash
curl -X PUT "$BASE_URL/obligations/$OBLIGATION_ID/assignment" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"operatorId\": $OPERATOR_ID,
    \"reason\": \"Asignación inicial del plan activo\"
  }"
```

Respuesta:

```json
{
  "obligationId": 510,
  "operatorId": 42,
  "assignmentVersion": 1
}
```

Guardar `assignmentVersion` si la interfaz presenta historial o confirma una reasignación. El
comando solo acepta una obligación cuyo estado persistido siga siendo `ASSIGNED`; cualquier otro
estado rechaza la asignación.

## 8. Verificar y entregar el trabajo al operario

El administrador puede verificar la asignación dentro de su empresa y sede:

```bash
curl "$BASE_URL/obligations/$OBLIGATION_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Fragmento esperado:

```json
{
  "id": 510,
  "assignedOperatorId": 42,
  "status": "ASSIGNED",
  "startsAt": "<INSTANTE_ISO_8601>",
  "dueAt": "<INSTANTE_ISO_8601>",
  "timezone": "America/Lima"
}
```

El operario inicia sesión y confirma la entrega con:

```bash
curl "$BASE_URL/assigned-obligations?status=ASSIGNED&limit=50" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

Desde ese punto continúa el
[happy path del operario](operator-flow.md#happy-path-completo-de-endpoints): iniciar ejecución,
completar pasos, adjuntar evidencia, revisar la versión autoritativa y enviar.

## Reglas de reintento, versión y alcance

- `POST /service-plans` requiere `Idempotency-Key`. El replay exacto devuelve el plan original;
  cambiar el contenido con la misma clave produce `IDEMPOTENCY_KEY_MISMATCH`.
- Activación y edición usan la versión más reciente devuelta por el servidor. Ante
  `VERSION_CONFLICT`, volver a consultar antes de decidir el reintento.
- Generación identifica una ocurrencia mediante `(definitionId, occurrenceDate)` y omite duplicados.
- Asignación se serializa sobre la obligación; repetir el mismo operario conserva la asignación
  actual sin crear otro cambio.
- Empresa, sede y actor proceden de la cuenta autenticada. No se aceptan IDs de otra empresa o
  sede aunque existan.
- El plan activo y las obligaciones conservan sus versiones fuente. Modificar trabajo futuro
  requiere una enmienda, no editar retrospectivamente la versión activa.

## Fuera de este happy path

- Checkout/webhooks de Stripe y cambios comerciales del plan de suscripción.
- Catálogos administrativos de empresas, sedes, servicios y usuarios, porque esas rutas todavía
  no existen en la API actual.
- Enmiendas de un plan activo mediante `POST /service-plans/{id}/amendments`.
- Suspensión, reactivación, cambio de rol/sede, cancelación o reenvío de invitaciones.
- Revisión de cumplimiento, acciones correctivas, alertas y reportes.
