# ADR-0009: Gateway-to-M7 service identity

- **Status:** Accepted for the first-release student assertion; Java issuance and indexing scope pending implementation
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7 team
- **Related ADRs:** [0007 Student guardrails](0007-student-access-guardrails.md)

## Context

React calls the Spring gateway with the end-user JWT. The gateway routes M7 public paths to core LMS, where Spring checks course entitlement and calls private FastAPI. M7 must know the verified user and course without trusting client-controlled identity headers. The Python verification side is now implemented for student assertions.

## Decision drivers

- Authenticated identity and course authorization cannot be forged by React.
- Internal credentials are short-lived, audience-limited and protected in transit.
- FastAPI can verify the identity context independently.
- The approach fits the existing gateway and deployment environment.

## Options considered

### Option A: Short-lived signed internal JWT with dedicated HS256 secret

- Benefits: carries subject, course, access type, issuer, audience and expiry; independently verifiable by M7.
- Costs and risks: both services need a shared secret and coordinated rotation; a stolen assertion remains valid until its short expiry.

### Option B: mTLS plus trusted identity headers

- Benefits: strong service identity at transport layer; avoids forwarding the end-user token as an internal credential.
- Costs and risks: M7 must only accept context from the authenticated gateway connection; proxy/header stripping and certificate rotation must be configured correctly.

### Option C: Forward the end-user JWT unchanged

- Benefits: simple propagation.
- Costs and risks: couples M7 to public token claims and issuer; unsuitable as the only service authentication mechanism.

## Proposal or decision

**Accepted for the student route boundary:** core LMS signs a dedicated HS256 assertion with `AI_TUTOR_SERVICE_JWT_SECRET` (at least 32 UTF-8 bytes), separate from the public JWT signing key. Required claims are `iss=oep-core-lms`, `aud=m7-ai-tutor`, `scope=ai:student`, `sub`, `course_id`, `request_id`, `jti`, `iat`, `nbf`, and `exp`; ID claims are UUIDs and timestamps are integer seconds. Lifetime is at most 60 seconds. The Python service accepts at most three seconds of clock skew and compares the token course and request ID with the path/header. Invalid assertions receive 401. Only core LMS may mint these tokens after current Student/course/enrollment/quiz checks. The private service must be reachable only over the internal network; deployment supplies the shared secret and transport encryption. Indexing-service scope and coordinated secret rotation are separate integration work.

## Consequences

Core LMS authorization must occur before proxying. FastAPI validates the dedicated assertion claims. All public reads and sends still re-check current course entitlement through core LMS. Revoking course access blocks new Java-issued assertions immediately; an already issued token expires within 60 seconds. The signed `request_id` ties the token to its request header, while chat idempotency protects duplicate persisted turns.

## Revisit when

Revisit when the platform security team publishes a shared service-auth standard or when deployment requires mTLS or overlapping key rotation.

## References

- [M7 trust-boundary requirements](../docs/m7-ai-tutor.md)
- [Gateway configuration](../../backend/api-gateway/src/main/resources/application.yml)
