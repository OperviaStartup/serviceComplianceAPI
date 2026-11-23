# Verificación PostgreSQL y E2E

`.github/workflows/backend-e2e.yml` se ejecuta en pull requests hacia `develop` y en pushes a
`develop` o `main`. Cada job crea una base PostgreSQL 16 aislada; no usa la base persistente de un
desarrollador ni credenciales externas.

## Gates

1. `full-regression` ejecuta toda la suite sobre la base H2 aislada de pruebas. En conjunto recorre
   los flujos implementados de identidad, planificación, generación/asignación, operación de campo,
   sincronización offline, reporting y revisión de cumplimiento.
2. `postgres-migrations` ejecuta todos los `*MigrationTests` sobre PostgreSQL 16 y Flyway.
3. `postgres-critical-flows` usa una base nueva por job de matriz para assigned-work, operator,
   offline-sync, reporting, compliance-review y compliance-decision. Esto evita que fixtures históricos de una suite
   contaminen otra.
4. `api-e2e` ejecuta por separado `*E2ETests` sobre PostgreSQL y un servidor Spring real en puerto aleatorio.
   Este gate hace visible una rotura del happy path HTTP aunque el resto de la regresión también
   tenga fallos.

Todos los jobs publican los reportes HTML de Gradle incluso cuando fallan. El workflow no despliega,
publica contratos ni llama Resend, Stripe o Supabase reales.

## Equivalente local

La regresión completa aislada en H2 se reproduce con:

```bash
./gradlew --no-daemon test
```

Para los gates PostgreSQL, use una base desechable llamada `field_operations_test` y configure:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/field_operations_test
export SPRING_DATASOURCE_USERNAME=service_user
export SPRING_DATASOURCE_PASSWORD=service_password
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
export SPRING_FLYWAY_ENABLED=true
export FIELD_OPERATIONS_TEST_JDBC_URL=$SPRING_DATASOURCE_URL
export FIELD_OPERATIONS_TEST_DB_USER=$SPRING_DATASOURCE_USERNAME
export FIELD_OPERATIONS_TEST_DB_PASSWORD=$SPRING_DATASOURCE_PASSWORD
./gradlew --no-daemon test --tests '*MigrationTests'
```

Los flujos críticos se ejecutan individualmente con `--tests`, recreando la base antes de cada clase,
igual que los jobs aislados de la matriz. Por ejemplo:

```bash
./gradlew --no-daemon test \
  --tests 'com.api.servicecompliance.OperationalStatusIntegrationTests'
```

Crear, vaciar o eliminar esa base es una acción local separada. Nunca apunte estas variables a una
base persistente o con datos valiosos.
