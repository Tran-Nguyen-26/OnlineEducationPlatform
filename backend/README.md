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
status. The learner's course detail can play unlocked HLS assets when the
course is published and the learner has active access. The API
checks the learner and the Redis stream token on each media request. Upload a
VTT or SRT file in the same lesson with the same LO to provide a clickable
transcript. This delivery path uses the application gateway and Core LMS.

Apply V11 for existing schemas to track individual document opens. Sequential
unlock checks the per-video Redis watch counters, all attached PDF/DOCX opens,
and a graded Mini-quiz score of at least 70 percent. Fast-track uses graded
first Pre-test results at 85 percent per LO. The learner assessment API serves
the question snapshot without the answer key and stores the grade in PostgreSQL
when the learner submits a Mini-quiz, Pre-test or Post-test.

Apply V12 for existing schemas to enforce one supplemental Post-test attempt
per enrollment and quiz. Associate each Post-test with a lesson in the target
module. A learner who exhausts regular attempts without a passing grade can
start the supplemental attempt after 24 hours and sufficient new heartbeat
watch time for every video in the module. Review counters use Redis.

Apply V13 for scheduled exams. Admins can set the exam window and late entry
in Course Builder. The learner's browser keeps a draft in IndexedDB and sends
versioned answer changes to PostgreSQL. The server refuses changes at cutoff,
accepts final submission of the saved draft during the 30-second grace window,
and collects unfinished attempts after grace. This initial path grades in Core
LMS, with a scheduled collector for abandoned attempts.

When the learner confirms a device transfer, the server changes the device
binding and appends an audit entry in the same transaction. The new device
resumes from the last acknowledged server draft. The audit IP is the remote
address supplied by the servlet container and depends on trusted proxy setup.

Apply V14 to store CSAT and NPS responses and the course graduation threshold.
The course results screen uses official Post-test, Mass Exam and assignment
scores with the configured weights. Within each quiz category, the configured
retake policy selects the score for each quiz and those scores are averaged.
A pending grade in a weighted category leaves the final score pending.

Apply V15 for course credentials. Set `OEP_BADGES_SIGNING_KEY_FILE` to a
mounted private RSA JWK (minimum 2048 bits), `OEP_BADGES_PUBLIC_ORIGIN` to the
public frontend origin that also serves the gateway under `/api`, and
`OEP_BADGES_ISSUER_NAME` to the issuer's display name. Preserve the signing
key and origin across deployments so previously issued credentials remain
verifiable. The learner can request and download a signed credential once
eligible. The public QR page validates the stored signature and displays
the course, issuer and issue date. The subject uses an opaque per-badge ID.

Apply V16 to enable timestamped lesson discussions. Learners ask from a
video timestamp and reply on unlocked lessons. Assigned instructors can
verify one reply per question. Archived learners retain read access.
Replies notify the question author through the existing notification outbox.

Admins can create course assignments with a rubric tied to the course's
learning outcomes. Learners submit text or a PDF/DOCX of up to 20 MB.
File submissions retain the extracted text for anonymous grading.
An existing submission can be revised before the deadline and before
the first instructor claim, subject to the configured submission limit.

The learner's outcome mastery map divides earned points by possible points
for each outcome. It uses the latest graded attempt of each visible-score
Mini-quiz, Post-test and Mass Exam, plus the latest published rubric grades.
Pre-test remains diagnostic. An outcome without graded evidence displays
no score, and an archived learner can still read their own outcome map.
