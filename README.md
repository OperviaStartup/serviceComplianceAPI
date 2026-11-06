# serviceComplianceAPI

API REST para gestionar el cumplimiento de obligaciones operativas, sus ejecuciones y sus evidencias.

## Contrato de detalle para Android

La primera ampliación de Field Operations agrega pasos, requisitos fotográficos y ventana de
ejecución a la creación directa de obligaciones, junto con `GET /api/v1/obligations/{id}`.
Consulte [el contrato, permisos, compatibilidad y límites del incremento](docs/field-operations/obligation-detail.md).
El [contrato del flujo del operario](docs/field-operations/operator-flow.md) agrega inicio,
avance, fotografías privadas, impedimentos, envío y vencimiento. La conexión a la instancia
compartida y la configuración del almacenamiento real se verifican por separado.

## Tecnologías

![Java](https://img.shields.io/badge/Java-17-2d2d2d)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-2d2d2d)
![Gradle%20Wrapper](https://img.shields.io/badge/Gradle%20Wrapper-9.7.1-2d2d2d)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-2d2d2d)
![Docker](https://img.shields.io/badge/Docker-Compatible-2d2d2d)
![Spring%20Security](https://img.shields.io/badge/Spring%20Security-Enabled-2d2d2d)
![Spring%20Data%20JPA](https://img.shields.io/badge/Spring%20Data%20JPA-Enabled-2d2d2d)
![Flyway](https://img.shields.io/badge/Flyway-Enabled-2d2d2d)
![OpenAPI%2FSwagger](https://img.shields.io/badge/OpenAPI%2FSwagger-Enabled-2d2d2d)
![JWT](https://img.shields.io/badge/JWT-Enabled-2d2d2d)

## Características principales

- API REST construida con Spring Boot.
- Autenticación y autorización con Spring Security.
- Manejo de sesiones autenticadas mediante JWT.
- Persistencia con Spring Data JPA y PostgreSQL.
- Migraciones automáticas con Flyway.
- Documentación de API con OpenAPI/Swagger.
- Ejecución local con Gradle Wrapper y despliegue con Docker/Render.

## Estructura del proyecto

```text
serviceComplianceAPI/
├── Dockerfile
├── build.gradle
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── render.yaml
└── src/
    ├── main/
    │   ├── java/com/api/servicecompliance/
    │   │   ├── authentication/
    │   │   ├── obligations/
    │   │   ├── executions/
    │   │   └── shared/
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           └── V1__create_core_schema.sql
    └── test/
```

## Requisitos

- Java 17.
- PostgreSQL 16 (o compatible para desarrollo local).
- Gradle local no obligatorio (se usa `./gradlew`, wrapper 9.7.1).
- Docker (opcional, para ejecución en contenedor).

## Variables de entorno

La aplicación usa las siguientes variables (según `application.properties` y `render.yaml`):

| Variable | Descripción | Ejemplo local |
|---|---|---|
| `PORT` | Puerto HTTP de la API | `8080` |
| `DB_HOST` | Host de PostgreSQL | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base de datos | `service_compliance` |
| `DB_USERNAME` | Usuario de PostgreSQL | `service_user` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `service_password` |
| `JDBC_DATABASE_URL` | URL JDBC completa (opcional, alternativa a `DB_*`) | `jdbc:postgresql://localhost:5432/service_compliance` |
| `JWT_SECRET` | Clave de firma para JWT | `cambiar-en-entorno-real` |
| `JWT_EXPIRATION_MS` | Expiración del token de acceso en milisegundos (30 minutos por defecto) | `1800000` |
| `BOOTSTRAP_SUPERVISOR_EMAIL` | Email del supervisor inicial | `supervisor@empresa.com` |
| `BOOTSTRAP_SUPERVISOR_PASSWORD` | Contraseña del supervisor inicial | `cambiar-en-entorno-real` |

## Configuración de base de datos local

Ejemplo de creación en PostgreSQL:

```sql
CREATE DATABASE service_compliance;
CREATE USER service_user WITH PASSWORD 'service_password';
GRANT ALL PRIVILEGES ON DATABASE service_compliance TO service_user;
```

Si aplica en tu instalación, otorga permisos sobre el esquema `public`:

```sql
\c service_compliance

GRANT ALL ON SCHEMA public TO service_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO service_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO service_user;
```

## Ejecutar localmente

Clona y entra al proyecto:

```bash
git clone https://github.com/OperviaStartup/serviceComplianceAPI.git
cd serviceComplianceAPI
```

Configura variables de entorno.

### Linux / macOS

```bash
export PORT=8080
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=service_compliance
export DB_USERNAME=service_user
export DB_PASSWORD=service_password
export JWT_SECRET=local-development-secret-change-this
export JWT_EXPIRATION_MS=1800000
```

### Windows PowerShell

```powershell
$env:PORT="8080"
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="service_compliance"
$env:DB_USERNAME="service_user"
$env:DB_PASSWORD="service_password"
$env:JWT_SECRET="local-development-secret-change-this"
$env:JWT_EXPIRATION_MS="1800000"
```

Da permisos al wrapper (Unix) y ejecuta:

```bash
chmod +x gradlew
./gradlew bootRun
```

En Windows:

```powershell
.\gradlew.bat bootRun
```

La API queda disponible en `http://localhost:8080`.

## Pruebas y build

Linux/macOS:

```bash
./gradlew test
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

## Docker

Construcción de imagen:

```bash
docker build -t service-compliance-api .
```

Ejecución (ejemplo):

```bash
docker run --rm \
  -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=service_compliance \
  -e DB_USERNAME=service_user \
  -e DB_PASSWORD=service_password \
  -e JWT_SECRET=local-development-secret-change-this \
  -e JWT_EXPIRATION_MS=1800000 \
  service-compliance-api
```

En Linux, también puedes usar `--network host` cuando corresponda.

## Migraciones

Flyway está habilitado (`spring.flyway.enabled=true`) y ejecuta migraciones al iniciar.

Migración inicial:

```text
src/main/resources/db/migration/V1__create_core_schema.sql
```

Además, Hibernate valida esquema (`spring.jpa.hibernate.ddl-auto=validate`), por lo que las migraciones deben aplicarse correctamente.

## Documentación OpenAPI

- JSON OpenAPI: `http://localhost:8080/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Despliegue con Render

El archivo `render.yaml` define:

- Servicio web Docker (`Dockerfile`).
- Base de datos PostgreSQL con `postgresMajorVersion: 16`.
- Health check en `/health`.
- Variables de conexión de base de datos.
- Generación automática de `JWT_SECRET`.
- Variables de bootstrap para supervisor.
