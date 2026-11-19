# ADR-0005: Answer-generation model and provider

- **Status:** Accepted for initial runtime; provider-neutral adapter contract
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7 project team
- **Related ADRs:** [0002 Embeddings](0002-embedding-model-and-dimensions.md), [0004 Retrieval](0004-retrieval-and-ranking.md), [0009 Service identity](0009-service-to-service-identity.md)
- **Model comparison:** [ADR-AI-001](0014-model-selection.md)

## Context

The AI Tutor sends student questions, bounded conversation history and retrieved course excerpts to a generation model. The team intends to start with Groq hosting `openai/gpt-oss-120b`, while requiring the implementation to work with Groq, OpenAI and Gemini providers and allow later model changes through configuration. This is an initial runtime choice, not a commitment to a single provider in the RAG code.

## Decision drivers

- Answer quality in Vietnamese and English, including abstention and citation adherence.
- Latency, token cost, rate limits and regional availability.
- Provider data handling and the project's rights to submit learning materials.
- Stable model versions and reproducible evaluation.
- No model authority over course access, citations or system safety rules.

## Options considered

### Option A: Hosted model on GCP Vertex AI

- Benefits: aligns with the documented GCP environment and cloud service identities.
- Costs and risks: provider-specific APIs and model lifecycle; availability and cost still require verification.

### Option B: Another hosted model API

- Benefits: allows selecting on evaluated quality, cost or existing team access.
- Costs and risks: sends course excerpts to another provider and adds a separate credential/data boundary.

### Option C: Self-host an open model

- Benefits: more control over data path and serving configuration.
- Costs and risks: GPU/runtime operations, scaling, updates and quality evaluation become team responsibilities.

## Proposal or decision

**Accepted:** define a `GenerationProvider` interface that accepts normalized system instructions, history, retrieved context and user question and returns normalized answer text, usage and errors (plus token events for later SSE). Implement separate Groq, OpenAI and Gemini adapters. Configure `generation.provider=groq` and `generation.model=openai/gpt-oss-120b` initially; never branch business rules on a provider/model ID. Provider-specific API payloads and SDKs stay inside adapters. Keep tool use disabled for the grounded-answer path unless separately approved; server-side citation checks remain authoritative. The first release returns one JSON response; SSE is a later functional enhancement.

## Consequences

Model output is untrusted. The server validates source IDs and constructs citations from retrieved records. Provider errors return a safe service error; they do not produce fabricated answers. Prompt and completion token counts and latency are normalized where available without logging full conversations by default. Changing generation provider/model does not require re-indexing, but still requires regression checks for answer and citation behavior.

## Revisit when

Revisit when provider terms, model availability, measured cost/latency or the project's data residency requirements change.

## References

- [M7 answer-generation rules](../docs/m7-ai-tutor.md)
- [Project GCP architecture](../../backend/CLAUDE.md)
- [Groq GPT-OSS 120B model](https://console.groq.com/docs/model/openai/gpt-oss-120b)
- [Groq API compatibility](https://console.groq.com/docs/openai)
- [Gemini API documentation](https://ai.google.dev/gemini-api/docs)
