# M2 implementation plan

This file tracks code implementation in the repository. A design/API contract is not counted as implemented until its backend behavior exists. Automated tests and runtime verification are tracked separately.

## Progress

| Work item | Status | Notes |
| --- | --- | --- |
| M2.1 Course, module and lesson CRUD | Implemented; manual API smoke verified | Create/read/list/update paths for courses, modules and lessons passed against local PostgreSQL on 2026-10-22. Full backend Maven build passed with tests skipped; automated tests remain outstanding. See scope and limits below. |
| M2.2 Reordering modules and lessons | Implemented | Complete-permutation APIs, parent-row locks, collision-safe two-phase position updates, and unique parent-position constraints added. Seven service unit tests pass; DB concurrency/API integration checks remain outstanding. |
| M2.3 Lesson TEXT/LINK content CRUD | Implemented | TEXT/LINK list/create/read/update/delete available to OWNER/CO_INSTRUCTOR. Six service unit tests and gateway API smoke checks against local PostgreSQL pass. File operations and student entitlement reads stay in M2.5/M2.6. |
| M2.4 Course lifecycle and instructor management | Implemented; unit and API smoke verified | Owner publish/unpublish/archive/restore and ADMIN disable/enable endpoints added; instructor list/add/remove added. Publish uses the OpenAPI's provisional readiness rule (at least one lesson and one AVAILABLE content item). Nine M2.4 unit tests pass, and direct Core LMS API smoke checks against PostgreSQL pass. Student instructor-list entitlement and ownership transfer remain out of scope. |
| M2.5 Outline and enrollment-aware student access | Implemented | Learner playback/mastery routes enforce enrollment and lesson unlock rules; ACTIVE and ARCHIVED_STATIC access are handled by playback/quota services. |
| M2.6 File storage and signed URLs | Implemented | Admin upload uses S3-compatible presigned PUT, server-side size/type confirmation, worker processing and signed learner playback/download URLs. |
| M2.7 Cross-module deletion/reference checks | Implemented | Empty modules and unreferenced lessons can be deleted; 409 guards protect lessons with content, progress, quizzes, assignments, discussions, completion rules or AI history. Course removal remains archive-only. |
| M2.8 Integration tests, API contract review and deployment | Not started | To be done after the implementation slices and shared contracts are ready. |

## M2.1 delivered scope

- `POST /api/v1/courses`: create a DRAFT course and its OWNER association in one transaction.
- `GET /api/v1/courses`: paginated PUBLIC + PUBLISHED catalog with optional title search.
- `GET /api/v1/courses/{courseId}`: public published course metadata, or management read for a course manager.
- `GET /api/v1/my/courses`: paginated courses where the caller is an OWNER or CO_INSTRUCTOR.
- `PATCH /api/v1/courses/{courseId}`: title, description, learning mode and start/end dates. Course lifecycle cannot be changed through PATCH.
- Module list/create/read/update and lesson list/create/read/update endpoints under `/api/v1/courses/...`.
- Course/module/lesson ownership checks, parent-chain validation, paging limits, DTO validation, default values and basic date/position/default-module checks.
- Gateway route and public GET access for the public course catalog and public course metadata.

Capacity changes and PUBLIC/PRIVATE visibility changes are rejected by course PATCH until M3 defines occupied-seat semantics and invite-code/enrollment behavior. Module/lesson read routes currently serve course managers; enrollment-aware student reads are deferred to M2.5. Delete and reorder endpoints are not included in M2.1. Course creation currently treats `User.role = INSTRUCTOR` as the approved instructor signal; confirm this against the instructor-approval flow before production use.

## M2.2 delivered scope

- `PATCH /api/v1/courses/{courseId}/module-order` reorders every module in a course using `ordered_ids`.
- `PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lesson-order` reorders every lesson in a module using `ordered_ids`.
- Both operations reject missing, duplicate, or out-of-parent IDs and run in one transaction.
- Reorder locks the parent row (`courses` for modules, `course_modules` for lessons), first moves children into distinct temporary negative positions, flushes, then writes contiguous zero-based positions and flushes again. The corresponding create operation locks the same parent to serialize position allocation with reorder.
- Added database unique constraints for `(course_id, position)` on modules and `(module_id, position)` on lessons.
- `mvn -pl core-lms-service -am test` succeeded: seven service unit tests cover valid module/lesson reorder, contiguous resulting positions, omitted/duplicate/foreign IDs, and missing course-instructor association. Database concurrency/API integration checks remain outstanding.

## M2.3 delivered scope

- Added paginated content list (optional upload-status filter), create, read, update and delete endpoints under each lesson.
- Current implementation accepts TEXT and LINK mutations only. TEXT requires `text_content`; LINK requires an absolute HTTP(S) `external_url`. Both become `AVAILABLE` immediately. File content mutation is deferred to M2.6.
- Only OWNER/CO_INSTRUCTOR can read or mutate these endpoints. Student entitlement-based reads remain in M2.5.
- Content responses omit `object_key`. Create appends after the current maximum position by default, serializes allocation with a lesson-row lock, and the entity has a unique `(lesson_id, position)` constraint.
- Deletion locks the content row and returns 409 when lesson progress, AI chunks or content change history reference it.
- `mvn -pl core-lms-service -am test` succeeded: six content-management unit tests plus the seven M2.2 tests (13 total).
- Gateway smoke checks against Core LMS and local PostgreSQL passed: TEXT creation returned 201/AVAILABLE; list, read and update returned 200; invalid `javascript:` LINK returned 422; delete returned 200 and a subsequent read returned 404. Responses omitted `object_key`. Temporary test users and courses were removed afterward.

## M2.4 delivered scope

- Added OWNER-only `POST /courses/{courseId}/publish`, `unpublish`, `archive` and `restore`, with locked course rows and explicit transition validation.
- Publishing currently follows the provisional OpenAPI readiness rule: the course must contain at least one lesson and at least one `AVAILABLE` content item. TEXT/LINK items created through M2.3 meet the content condition because they are immediately AVAILABLE.
- Added ADMIN-only `disable` and `enable`; enabling returns the course to DRAFT for owner review.
- Added OWNER-only co-instructor add/remove and manager-only instructor listing. Added instructors must be active system-level INSTRUCTOR users; the course OWNER cannot be removed. Course-row locking serializes instructor membership changes.
- Student access to the instructor list still needs M3 enrollment entitlement integration. Ownership transfer is not part of the current API contract.
- `CourseLifecycleAndInstructorManagementTest` covers readiness, transitions, role restrictions, instructor assignment, and owner-removal protection. All nine M2.4 unit tests pass.
- `mvn -pl core-lms-service -am test` succeeded on 2026-10-23: all 22 Core LMS service tests passed (9 M2.4, 7 M2.2, 6 M2.3).
- Direct Core LMS API smoke checks against the project's PostgreSQL primary/replica passed on 2026-10-23: empty-course publish returned 409 `COURSE_NOT_READY`; publish/unpublish/republish, archive/restore, ADMIN disable/enable and co-instructor add/list/remove passed; co-instructor archive and instructor disable returned 403.
- Smoke-test course and accounts were removed and verified absent afterward. Core LMS and Docker dependencies were stopped after the check. Gateway-level smoke testing remains outstanding.

## Implementation notes

- Existing JPA entities are in `core-model`; this slice adds Core LMS repositories, DTOs, service and controller.
- Schema generation remains the existing development `ddl-auto: update` setup. M2.2 and M2.3 unique constraints are declared on the entities; production migrations have not been added yet.
- Core catalog search uses a non-null normalized search term to avoid PostgreSQL inferring a null parameter as `bytea`.
- Create and update operations flush before building their response so generated `created_at` and `updated_at` values are included.

## Verification (2026-10-22)

- `mvn install -DskipTests` succeeded for all backend modules (`core-model`, `api-gateway`, `core-lms-service`, `assessment-service`, and `worker-service`). Automated tests were skipped and no new automated tests were added.
- Manual API smoke checks passed for login, public catalog listing, and course/module/lesson create, read, list, and update operations against the local PostgreSQL containers.
- The public catalog returned an empty list while the newly created course was `DRAFT`; this is expected because the catalog only includes `PUBLIC` + `PUBLISHED` courses.
- Vietnamese request data was confirmed stored as UTF-8 in PostgreSQL. Windows PowerShell 5.1 displayed some JSON response text with mojibake; this was a client display/decoding issue.
- M2.3 local gateway/PostgreSQL API smoke checks passed on 2026-10-22 after installing the updated `core-model` artifact. The process used `TZ=UTC` because PostgreSQL rejected the host-provided `Asia/Saigon` session timezone.

## M4 Quiz Engine delivered scope (2026-11-01)

- Added full CRUD Quiz, Question Bank, Questions + Options in `core-lms-service` for Instructor and Admin roles.
- `POST /api/v1/courses/{courseId}/quizzes`: create Quiz with assessment kind (`PRE_TEST`, `MINI_QUIZ`, `POST_TEST`, `MASS_EXAM`), question source, time limits, deadline, max attempts, grading policy, and shuffle settings.
- `PATCH /api/v1/courses/{courseId}/quizzes/{quizId}`: update quiz metadata; blocks updates if `is_locked` is true (`QUIZ_NOT_EDITABLE`).
- `DELETE /api/v1/courses/{courseId}/quizzes/{quizId}`: blocks deletion if attempts exist (`QUIZ_HAS_ATTEMPTS`).
- Question Bank CRUD: `GET/POST/PATCH/DELETE /api/v1/courses/{courseId}/question-banks`; blocks deletion when non-empty (`QUESTION_BANK_NOT_EMPTY`).
- Questions & Options: embedded option management, option validation for `TRUE_FALSE`, `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, and `KEYWORD_FILL`; questions can be tagged with Learning Outcome and Bloom level and imported/exported as CSV/JSON.
- `POST /api/v1/courses/{courseId}/quizzes/{quizId}/questions/reorder`: two-phase collision-safe reordering with negative temporary positions.
- Score adjustment: `POST /api/v1/courses/{courseId}/quizzes/{quizId}/results/{resultId}/adjust-score`; validates `final_score <= sum(points)` (`SCORE_OUT_OF_RANGE`).
- Admin Quiz overview: `GET /api/v1/admin/quizzes` with pagination, course filter, and attempt counts.
- Service unit tests: `QuizManagementServiceTest` (11 test cases) all pass.
- Worker score sync now consumes `ExamScoreSyncEvent`, validates the attempt/quiz/enrollment references and score range, locks the attempt row, creates the unique result once, and marks the attempt `GRADED`. Backend build/runtime verification remains skipped because Java/Maven are unavailable in the current environment.
- Mass-exam draft sync now returns the accepted sequence and server timestamp; the learner exam UI distinguishes local IndexedDB save, in-progress save and server-confirmed sync time. Frontend build passes; backend runtime verification remains skipped in this environment.
- Admin email recovery now lists paged FAILED delivery events and lets an active ADMIN replay one, resetting retry scheduling and recording an audit action. Frontend build/runtime verification is tracked separately; backend runtime verification remains skipped in this environment.
- Quiz question CRUD now persists Learning Outcome and Bloom metadata that already existed in the schema; a course-manager endpoint supplies allowed outcomes to the editor. The editor shows these tags and supports KEYWORD_FILL authoring plus validated CSV/JSON import/export. Exports can target the selected bank or full quiz; imports assign questions to the selected bank and preserve LO/Bloom tags. Frontend production build passes; backend runtime verification remains skipped.
- Quiz create/update now exposes all four assessment kinds (PRE_TEST, MINI_QUIZ, POST_TEST, MASS_EXAM) in the editor and carries the selected kind through the existing `assessment_kind` column and API response. Frontend production build passes; backend runtime verification remains skipped.
- Legacy quizzes whose kind is still null are treated as MINI_QUIZ by the learner assessment query; new quizzes default to MINI_QUIZ, and migration V26 backfills existing rows when the migration scripts are applied.
- Question Bank frontend now exposes rename in addition to create/delete, matching the existing backend PATCH endpoint.
- Rabbit notification events now persist an idempotent in-app notification and enqueue email delivery when each channel is enabled in the recipient's preferences. Unknown recipients fail for broker retry/DLQ instead of creating placeholder users; the SQS path uses the same missing-recipient behavior.
- Admin AI metrics now include mistake-explanation cache hit/miss counts and hit rate for the selected date range. The service records anonymous events in migration V27; video egress remains an infrastructure-side metric and is skipped with blocked infrastructure/NFR work.
- Admin AI metrics calculate estimated USD cost per day, course and selected date range from configured input/output USD-per-million-token rates. Rates default to unconfigured/zero and must be supplied via `OEP_AI_COST_INPUT_USD_PER_MILLION` and `OEP_AI_COST_OUTPUT_USD_PER_MILLION`. Migration V29 aggregates estimated usage for AI Tutor, uncached mistake explanations and AI question drafting.
- The Admin exam panic switch now pauses AI Tutor, AI quiz drafting, uncached mistake explanations, course analytics and Admin AI metrics in addition to new mass-exam attempts; existing attempts and cached mistake explanations remain available. The settings page documents the expanded behavior.
- Notification outbox events are emitted for course activation/enrollment, mass-exam scheduling and certificate issuance. Email and in-app delivery continue to follow the recipient's notification preferences and the existing worker retry flow.
- Learner course detail now mounts the existing LO mastery map for active and archived-static learners, and mounts assignment submission, graduation progress, CSAT/NPS and credential controls for active learners. When a learner's Post-test remediation becomes available after cooldown and required video review, the assessment listing enqueues one idempotent notification.
- Added an Admin regrade audit view backed by immutable `REGRADE` audit rows, showing original/final scores, difference, assignment, criterion, appeal reason, independent reviewer and the >15% alert threshold.
- Admin operations overview now reports PostgreSQL and Redis connectivity, pending transactional outbox events and failed email deliveries as the system health snapshot.
- AI Tutor now uses OpenAI-compatible embeddings and pgvector cosine similarity for lesson chunks when `AI_EMBEDDINGS_URL` is configured. The media worker extracts timestamped `.vtt/.srt` cues into `ai_document_chunks`, and a scheduled batch job backfills vectors for available chunks; lexical/transcript retrieval remains the fallback. Migration V28 creates the chunk table/vector column and HNSW index.
- Worker service now enqueues one deduplicated in-app/email reminder about 15 minutes before each scheduled mass exam using the existing transactional outbox and notification delivery pipeline.
- Instructor grading now offers optional AI rubric pre-grading while a valid claim lease is held. Suggestions include rubric-bounded scores and source quotes, are shown as drafts, and require instructor review; migration V29 records estimated usage for AI Tutor, pre-grading, mistake explanations and quiz drafting.
- Assignment submissions now receive two short AI Viva Voce questions. Learners can answer once; the service stores both answers and a PII-scrubbed, concept-consistency signal for the instructor's grading view. The signal is explicitly advisory and never labels a learner as cheating; when AI is unavailable, text-based questions remain available and the score is left unassessed. Migration V30 adds the Viva Voce fields.
- Lesson discussions now suggest up to three instructor-verified answers as a learner types a question. Instructor verification automatically upserts that Q&A pair into a scoped semantic index; an embedding worker indexes it, and AI Tutor retrieves it alongside lesson chunks. Full-text ranking remains available while embeddings are missing or unavailable. Migration V31 creates the verified Q&A index.
- Learning analytics now aggregates learner video rewind events into 30-second buckets and reports the most revisited segments. It also shows the most frequently selected incorrect multiple-choice distractors by question and LO. Rewind events are anonymous aggregates and require the active playback stream; migration V32 creates the heatmap table.
- FR-035 adaptive remediation is now implemented: learners below 70% on an LO receive lesson-linked video recommendations and up to five due single-choice/true-false micro-drills per weak LO. The review endpoint uses FSRS-4.5 default weights to update stability, difficulty and the 90% retention interval; incorrect responses enter a 10-minute relearning step. Both plan and answer routes honor exam locking and the Exam Panic Circuit Breaker. Migration V33 stores per-enrollment scheduling state. Runtime verification remains pending with the backend environment.
- Performance prewarming, automatic CPU/database thresholds, production load checks, video-egress telemetry and backend build/runtime verification are still not marked complete because they require infrastructure or tooling unavailable in this workspace. They are tracked as blocked/skipped rather than implemented.

