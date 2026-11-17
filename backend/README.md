# OEP Backend — Online Education Platform

Java 21 · Spring Boot 3.3 · Maven multi-module · PostgreSQL (primary + read replica) · Redis · RabbitMQ · S3-compatible storage.

## Modules

| Module | Port | Role |
|---|---|---|
| `api-gateway` | 8080 | WebFlux gateway: JWT + revocation check, per-route rate limits, idempotency keys, retry / circuit breaker, `/api/v1` alias, security headers |
| `core-lms-service` | 8081 | users, auth (JWT RS256 + JWKS), courses, enrollment, content, admin, privacy; **runs Flyway** |
| `assessment-service` | 8082 | quizzes, attempts, submissions, grading, appeals (reads from the replica) |
| `worker-service` | 8083 | outbox relay → RabbitMQ, notification consumer (retry + dead-letter queue) |
| `core-model` | — | shared jar: entities, enums, events, exceptions, `PageResponse`, `ApiExceptionHandler` |
| management port | 9090 | **prod only**: health / readiness / Prometheus on every service, never exposed publicly |

## Quick start (profile `dev`, the default)

```bash
docker compose up -d                              # postgres (+replica), redis, rabbitmq, minio
mvn -q -DskipTests install                        # builds core-model into ~/.m2
mvn -pl core-lms-service spring-boot:run          # FIRST: applies Flyway migrations, seeds admin@oep.local / Admin@12345
mvn -pl assessment-service spring-boot:run
mvn -pl worker-service spring-boot:run
mvn -pl api-gateway spring-boot:run               # frontend talks to http://localhost:8080/api/...
```
Requires `JAVA_HOME` = JDK 21. Swagger UI (dev only): <http://localhost:8081/swagger-ui.html>, <http://localhost:8082/swagger-ui.html>.

## Production-like run (profile `prod`, Docker)

```bash
scripts/gen-secrets.sh                            # .env.prod + JWT key (git-ignored)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
scripts/smoke.sh                                  # 13 end-to-end checks; app on http://localhost:8088
```

## Documentation map

| File | Content |
|---|---|
| `DEPLOYMENT.md` | profiles dev/prod, local prod-like stack, AWS options, environment variables, pre-demo checklist |
| `OPERATIONS.md` | runbook: backup/restore, async pipeline (outbox/DLQ), key rotation, monitoring, troubleshooting, ops/privacy endpoints |
| `../infra/aws/README.md` | Terraform for AWS (`ec2-demo`, `ecs-managed`) |
| `ENTERPRISE_READINESS.md` | capability audit + status table of reliability / security / observability work |
| `CLOUD_ALIGNMENT.md` | mapping of the project to the Cloud course (Project 8) requirements and remaining gaps |
| `CLAUDE.md` | architecture diagram, conventions, branch workflow |
| `DATABASE.md`, `FLOWS.md`, `BUSINESS.md`, `GAPS.md`, `STATUS.md` | schema, flows, business rules, gap analysis, delivery status |

## Schema

Flyway owns the schema (`core-lms-service/src/main/resources/db/migration`, V1 baseline … V4); Hibernate runs with `ddl-auto: validate`.
An existing database created by the old `ddl-auto: update` is baselined at V1 automatically. Only core-lms-service migrates; start it first.
