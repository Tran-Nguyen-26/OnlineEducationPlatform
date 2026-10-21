# M2 implementation plan

This file tracks code implementation in the repository. A design/API contract is not counted as implemented until its backend behavior exists. Automated tests and runtime verification are tracked separately.

## Progress

| Work item | Status | Notes |
| --- | --- | --- |
| M2.1 Course, module and lesson CRUD | Implemented; manually verified | Core LMS CRUD happy paths were exercised against the local PostgreSQL setup. Maven build passed with tests skipped; automated tests remain outstanding. See scope and limits below. |
| M2.2 Reordering modules and lessons | Not started | Requires atomic position updates and collision-safe persistence. |
| M2.3 Lesson TEXT/LINK content CRUD | Not started | File uploads/downloads excluded. |
| M2.4 Course lifecycle and instructor management | Not started | Publish readiness remains a proposal; add lifecycle and instructor endpoints after decisions. |
| M2.5 Outline and enrollment-aware student access | Not started | Private access and student module/lesson reads depend on M3 entitlement policy. |
| M2.6 File storage and signed URLs | Blocked on contract | `complete-upload` currently asks the caller for `object_key` while the upload response intentionally hides it. Storage provider, limits and URL TTLs also need a decision. |
| M2.7 Cross-module deletion/reference checks | Blocked on M3/M5/L4 integration | Lesson deletion must account for progress, quiz and assignment references. |
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

## Implementation notes

- Existing JPA entities are in `core-model`; this slice adds Core LMS repositories, DTOs, service and controller.
- Schema generation remains the existing development `ddl-auto: update` setup. Unique database constraints and production migrations have not been added yet.
- Maven build passed with `-DskipTests`; automated tests have not been added or run. Local API smoke checks covered create/read/list/update for courses, modules and lessons.
