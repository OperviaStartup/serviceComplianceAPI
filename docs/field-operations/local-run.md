# Entorno local para comprobar el frontend

## Configuración y alcance

El perfil explícito `field-local` selecciona `LocalPhotoStorage` como adaptador de fotografías.
Guarda los bytes reales en una carpeta privada del disco; no publica archivos estáticos ni
cambia los endpoints de evidencia, las autorizaciones, el checksum, los límites o las reglas
de ejecución. Sin ese perfil permanece seleccionado el adaptador Supabase existente. No es
un mecanismo de respaldo automático cuando falla Supabase ni una prueba contra ese proveedor.
La propiedad `app.field-operations.local-directory` es obligatoria y debe ser una ruta absoluta.
El adaptador rechaza claves fuera de su estructura, enlaces simbólicos y reemplazos de archivos.

Para la sesión local del 2026-11-05 se preparó:

- API en `127.0.0.1:8080`, perfil `field-local`.
- PostgreSQL 18 en `127.0.0.1:55439`, base exclusiva `field_operations_demo`.
- Clúster local identificado por `build/field-operations-review/postgres-test-state.json`.
  La base anterior `field_operations_test` se conserva y no se utiliza para la demostración.
- Migraciones Flyway V1–V11 y Hibernate `validate` sobre la base nueva.
- Cuenta de supervisor por el bootstrap existente; operario de prueba insertado únicamente
  en esta base local, con hash BCrypt y la misma empresa/centro. No se alteró autenticación.
- Obligación 1, «Limpiar recepción», con tres pasos y dos fotos; permanece asignada sin iniciar.
- Obligación 2 separada e identificada como prueba HTTP, completada por la verificación automática.

El lanzador generado `build/field-local/start.ps1` fija explícitamente por argumentos de Spring
las URLs Datasource y Flyway a la base local, usa credenciales aleatorias de demostración y
deja el proceso oculto con logs en esa misma carpeta. No carga un `.env` compartido. La sesión
del operario se obtiene usando el endpoint existente de login y dura cuatro horas en este entorno.
No se implementaron login ni listado en Kotlin.

## Abrir desde Android Studio

Con Run → **Flujo operario**, introducir:

| Campo | Valor |
| --- | --- |
| URL del backend | `http://10.0.2.2:8080` |
| ID de obligación | `1` |
| Token de sesión | Contenido de `build/field-local/operator-token.txt`, sin `Bearer` |

El token y la configuración privada están en `build`, excluido por `.gitignore`; no copiarlos
al código, al informe ni al chat. La ventana horaria concreta está en `build/field-local/demo.json`.
La API escucha solamente en loopback: esta URL está preparada para el emulador Android del equipo.

Abrir obligación, iniciar, marcar un paso y regresar para comprobar recuperación; capturar y
revisar las dos fotos antes de subirlas, registrar un impedimento y finalmente completar los
requisitos y enviar. Cada éxito depende de la confirmación del backend. El impedimento conserva
el avance y no exime las fotos. Las imágenes se consultan mediante la API autenticada.

Los datos y fotos de demostración están dentro de `build`; se conservan al reiniciar la app/API,
pero **no deben ejecutarse tareas `clean` ni borrarse esa carpeta mientras se quiera conservarlos**.
Esto es un entorno de demostración local; no se modificó la instancia desplegada del grupo.

## Evidencia de verificación

- `gradlew.bat build --no-daemon`: correcto, con las pruebas nuevas de almacenamiento local.
- `LocalPhotoStorageTests`: persistencia entre instancias, borrado idempotente, rechazo de
  reemplazo, claves inválidas y tamaño excesivo; ausencia del bean sin perfil explícito.
- Comprobación HTTP real sobre PostgreSQL: inicio único, rechazo del envío incompleto,
  impedimento sin completar, paso persistido, rechazo de versión antigua, subida y descarga
  exacta de un PNG de prueba, rechazo de descarga anónima y envío final con edición cerrada.
- Verificación de los bytes guardados en disco mediante SHA-256. La imagen automatizada
  pertenece solo a la obligación de prueba HTTP; no se precargan evidencias para el usuario.
- Resumen sin credenciales en `build/field-local/verification.json`.

El 2026-11-05 el usuario confirmó haber probado correctamente el flujo en Kotlin desde el
emulador, después de corregir el permiso de red local de Android 17 y ajustar la UI. Esta
confirmación corresponde al entorno local; no equivale a una verificación de Supabase ni
del backend desplegado. No se adjuntaron capturas de todo el recorrido en este documento.

## Pendientes para integración y despliegue

- Actualizar la rama de trabajo con los cambios recientes de `develop` y resolver cualquier
  conflicto antes de revisar/fusionar. La consulta del estado local no prueba que el remoto
  esté actualizado.
- Comprobar en Render la rama conectada y su configuración efectiva de despliegue automático.
  `render.yaml` declara `autoDeployTrigger: commit` y no fija rama. Fusionar puede desplegar
  si el servicio sigue `develop`; no asumir que integrar código y desplegar están separados.
- Antes del despliegue, identificar la base compartida, su historial Flyway, su respaldo y
  verificar V10/V11 sobre una copia. La comprobación PostgreSQL local previa fue en versión 18;
  `render.yaml` declara PostgreSQL 16, pendiente de comprobar contra la instancia efectiva.
- Configurar el almacenamiento privado real del backend y verificar subida/descarga con
  autorización en el entorno del grupo. Mantener desactivado `field-local` en ese entorno.
- Integrar con la sesión y el listado del compañero. US-08 permanece fuera de esta entrega.

Archivos fuente agregados: `LocalPhotoStorage.java`, `LocalPhotoStorageTests.java` y esta guía.
El adaptador de producción, autenticación, listado, contratos y migraciones no se modificaron.
