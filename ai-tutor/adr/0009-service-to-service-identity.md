# ADR-0009: Gateway-to-M7 service identity

- **Status:** Proposed
- **Decision date:** Pending
- **Decision owner(s):** Platform/security team
- **Related ADRs:** [0007 Student guardrails](0007-student-access-guardrails.md)

## Context

React calls the Spring gateway with the end-user JWT. The gateway checks course entitlement and calls private FastAPI. M7 must know the verified user, course and access type without trusting client-controlled identity headers. The current M7 draft illustrates a short-lived signed assertion and also mentions mTLS with verified context as an alternative.

## Decision drivers

- Authenticated identity and course authorization cannot be forged by React.
- Internal credentials are short-lived, audience-limited and protected in transit.
- FastAPI can verify the identity context independently.
- The approach fits the existing gateway and deployment environment.

## Options considered

### Option A: Short-lived signed internal JWT

- Benefits: carries subject, course, access type, issuer, audience and expiry; independently verifiable by M7.
- Costs and risks: signing-key distribution/rotation, replay protection and claim-contract ownership.

### Option B: mTLS plus trusted identity headers

- Benefits: strong service identity at transport layer; avoids forwarding the end-user token as an internal credential.
- Costs and risks: M7 must only accept context from the authenticated gateway connection; proxy/header stripping and certificate rotation must be configured correctly.

### Option C: Forward the end-user JWT unchanged

- Benefits: simple propagation.
- Costs and risks: couples M7 to public token claims and issuer; unsuitable as the only service authentication mechanism.

## Proposal or decision

**Proposal only:** use mTLS for service identity plus a short-lived signed assertion for verified user/course context, unless the platform's gateway already provides an equally strong standard. Never trust an unsigned `X-User-Id` or client-supplied course claim. Define issuer, audience, expiry, key rotation and replay behavior before implementation.

## Consequences

Gateway authorization must occur before proxying. FastAPI validates service identity and assertion claims. All public reads and sends still re-check current course entitlement through the gateway.

## Revisit when

Revisit when the platform security team publishes the shared service-auth standard.

## References

- [M7 trust-boundary requirements](../docs/m7-ai-tutor.md)
- [Gateway configuration](../../backend/api-gateway/src/main/resources/application.yml)
