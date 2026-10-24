# OEP Backend

Modules
- api-gateway         :8080  Spring Cloud Gateway: JWT check, Redis rate limit, path routing (no core-model dependency)
- core-model          shared jar: entities (41), enums, event contracts, exceptions
- core-lms-service    :8081  users, courses, enrollment, signed URLs
- assessment-service  :8082  quiz / submission, Redis for exam state
- worker-service      :8083  RabbitMQ consumers: notification, score sync

Run
    docker compose up -d
    mvn clean install
    mvn -pl core-lms-service spring-boot:run     # start this first (creates/updates the dev schema)
    mvn -pl assessment-service spring-boot:run
    mvn -pl api-gateway spring-boot:run          # frontend calls http://localhost:8080/api/...
    mvn -pl worker-service spring-boot:run

Schema: no Flyway for now. core-lms-service runs with ddl-auto=update (dev only) and creates the tables;
assessment/worker use ddl-auto=none, so start core-lms-service first.

## M2 integration smoke (M2.1-M2.6)

From the `backend` directory, start local dependencies:

```powershell
docker compose up -d postgres postgres-replica redis rustfs rabbitmq
```

In separate PowerShell terminals, start Core LMS and then the gateway from the `backend` directory.
Set Java's timezone before starting Core LMS; this avoids PostgreSQL rejecting the host's `Asia/Saigon`
timezone alias:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
mvn -pl core-lms-service spring-boot:run
```

After Core LMS creates/updates the shared schema, apply the manual M2 V2 script
to the PostgreSQL primary before running lifecycle audit flows or the smoke test:

```powershell
psql -h localhost -p 5434 -U oep -d oep -v ON_ERROR_STOP=1 -f core-model/src/main/resources/db/migration/V2__course_content_schema.sql
```

The local Compose password is `oep`. V2 is not auto-applied; see
`core-model/src/main/resources/db/migration/README.md` for prerequisites and limits.

```powershell
mvn -pl api-gateway spring-boot:run
```

From the repository root, run the Core LMS unit and OpenAPI contract tests, then the repeatable API smoke
flow:

```powershell
mvn -f backend/pom.xml -pl core-lms-service -am test
./backend/scripts/m2-integration-smoke.ps1
```

The smoke script uses the local Compose PostgreSQL container to grant its temporary owner account the
INSTRUCTOR role and create an ACTIVE enrollment. It removes the test content through the API, then removes
the test course and accounts from PostgreSQL. It does not delete Docker volumes. Set `BaseUrl`,
`PostgresContainer`, `DbName`, or `DbUser` parameters when using different local values.

This is a local development verification flow, not a production deployment recipe. Production migration,
secret management, and storage-provider configuration remain deployment work.
