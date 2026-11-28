# OEP Backend — Online Education Platform

Java 21 · Spring Boot 3.3 · Maven multi-module · PostgreSQL 16 (primary + read replica) · `ai-postgres` PG17 `pgvector` (profile `ai-tutor`) · Redis · RabbitMQ · S3-compatible storage (MinIO / AWS S3).

## Modules

| Module | Port | Role |
|---|---|---|
| `api-gateway` | 8080 | WebFlux gateway: JWT + revocation check, per-route rate limits, idempotency keys, retry / circuit breaker, `/api/v1` alias, security headers |
| `core-lms-service` | 8081 | users, auth (JWT RS256 + JWKS), courses, enrollment, content, HLS signed playlists, AI Tutor proxy, admin, privacy; **runs Flyway (`V1–V6`)** |
| `assessment-service` | 8082 | quizzes, attempts, submissions, blind grading, appeals (reads from the replica) |
| `worker-service` | 8083 | outbox relay → RabbitMQ, FFmpeg HLS `360p/720p` transcoder, notification consumer + optional SMTP `EmailSender` (retry + DLQ) |
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
scripts/gen-secrets.sh                            # .env.prod + JWT key + demo password (git-ignored)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
scripts/smoke.sh                                  # 13 end-to-end checks; app on http://localhost:8088
```
Sample data (teachers `gv.lan@oep.demo`, `gv.minh@oep.demo`; students `hs01..hs08@oep.demo`; password = `DEMO_PASSWORD` in `.env.prod`) is loaded by profile `demo` — see `DEPLOYMENT.md`. For dev: `SPRING_PROFILES_ACTIVE=dev,demo mvn -pl core-lms-service spring-boot:run`.

## Documentation map

| File | Content |
|---|---|
| `../docs/BRD_Project8_OEP_v1.4.md` | **Single Source of Truth** — Tài liệu yêu cầu nghiệp vụ & kiến trúc tổng thể (BRD v1.4) |
| `DEPLOYMENT.md` | profiles dev/prod, local prod-like stack, AWS options, environment variables, pre-demo checklist |
| `LOAD_TEST_REPORT.md` | kết quả load test (k6) và chaos test, các lỗi tìm thấy và đã sửa; scripts ở `loadtest/` |
| `../.github/workflows/` | CI (build, test, image, terraform validate), deploy và rollback lên ECS |
| `OPERATIONS.md` | runbook: backup/restore, async pipeline (outbox/DLQ), key rotation, monitoring, troubleshooting, ops/privacy endpoints |
| `../infra/aws/README.md` | Terraform for AWS (`ec2-demo`, `ecs-managed`) |
| `ENTERPRISE_READINESS.md` | capability audit + status table of reliability / security / observability work |
| `CLOUD_ALIGNMENT.md` | mapping of the project to the Cloud course (Project 8) requirements and remaining gaps (synced to BRD v1.4) |
| `CLAUDE.md` | architecture diagram, conventions, branch workflow |
| `DATABASE.md`, `FLOWS.md`, `BUSINESS.md`, `GAPS.md`, `STATUS.md` | schema (historical snapshot), flows (historical snapshot), business rules (synced v1.4), gap analysis (historical), delivery status |

## Schema

Flyway owns the schema (`core-lms-service/src/main/resources/db/migration`, `V1` baseline … `V6` BRD alignment); Hibernate runs with `ddl-auto: validate`.
An existing database created by the old `ddl-auto: update` is baselined at V1 automatically. Only core-lms-service migrates; start it first.
