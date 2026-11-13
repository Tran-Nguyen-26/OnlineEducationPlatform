# OEP Backend

Modules
- api-gateway         :8080  Spring Cloud Gateway: JWT check, Redis rate limit, path routing (no core-model dependency)
- core-model          shared jar: entities (41), enums, event contracts, exceptions
- core-lms-service    :8081  users, courses, enrollment, signed URLs (runs Flyway)
- assessment-service  :8082  quiz / submission, Redis for exam state
- worker-service      :8083  RabbitMQ consumers: notification, score sync

Run
    docker compose up -d
    mvn clean install
    mvn -pl core-lms-service spring-boot:run     # start this first (applies migrations)
    mvn -pl assessment-service spring-boot:run
    mvn -pl api-gateway spring-boot:run          # frontend calls http://localhost:8080/api/...
    mvn -pl worker-service spring-boot:run

Schema: no Flyway for now. core-lms-service runs with ddl-auto=update (dev only) and creates the tables;
assessment/worker use ddl-auto=none, so start core-lms-service first.
### Lesson media upload

Set `OEP_MEDIA_ENDPOINT`, `OEP_MEDIA_BUCKET`, `OEP_MEDIA_ACCESS_KEY` and
`OEP_MEDIA_SECRET_KEY` in Core LMS and the worker. Keep the bucket private.
Allow browser PUT requests from the Admin Portal origin in the bucket CORS policy.
Run the worker with FFmpeg and FFprobe available on PATH.

In Admin Courses, open a draft course, add a learning outcome and select a lesson
to upload an MP4, PDF, DOCX, VTT or SRT file. Core LMS issues a short-lived signed
PUT URL and validates the stored size and content type on confirmation. The worker
processes confirmed uploads, generates 480p/720p HLS for MP4 and updates the content
status. Private playback sessions and delivery of subtitles to the player are
separate integration work.
