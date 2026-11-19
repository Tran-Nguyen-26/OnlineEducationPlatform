# ADR-0002: Embedding model and dimensions

- **Status:** Accepted for initial model; adapter boundary remains provider-neutral
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7 project team
- **Related ADRs:** [0001 Vector database](0001-vector-database.md), [0003 Chunking](0003-content-parsing-and-chunking.md)
- **Model comparison:** [ADR-AI-001](0014-model-selection.md)

## Context

An earlier design snapshot assumed 1536 dimensions without naming the model; that source is absent from the current checkout. The Java entity currently stores vectors as text. The team chose **Jina AI `jina-embeddings-v5-text-small`** for the initial embedding configuration; its full output is 1024 dimensions. A retrieval index only compares vectors from the same embedding space, so changing model or dimensions requires re-embedding indexed sources and matching query/document task modes.

## Decision drivers

- Quality on the project's actual Vietnamese and English course material.
- Data handling, region, authentication and provider terms.
- Cost and throughput for batch document indexing and interactive questions.
- Vector dimensions, storage and index cost.
- Model lifecycle and the ability to re-index reproducibly.

## Options considered

### Option A: Google hosted embedding through Vertex AI

- Benefits: fits the GCP architecture and service identity; current Google documentation lists multilingual embedding models and configurable output dimensions.
- Costs and risks: provider dependency, model availability/region constraints and recurring inference costs; model changes still require a full re-embedding cycle.

### Option B: Another hosted embedding API

- Benefits: may fit an already selected LLM provider or offer better measured quality/cost.
- Costs and risks: additional provider/data boundary; provider dimensions and lifecycle may constrain database schema.

### Option C: Self-host an open multilingual model

- Benefits: more control over runtime and data flow; avoids per-request hosted embedding charges after deployment.
- Costs and risks: serving, hardware, scaling and model operations become team responsibilities; quality and latency must be measured.

## Proposal or decision

**Accepted:** define an `EmbeddingProvider` interface for document batches and query text, with provider/model/version, output dimension and task mode recorded in an `EmbeddingSpace` configuration. Implement Jina, OpenAI and Gemini adapters. Start with Jina `jina-embeddings-v5-text-small`, `dimensions=1024`, `task=retrieval.passage` for chunks and `task=retrieval.query` for questions. Select provider/model through configuration; business logic and APIs never branch on them. Configure the selected vector store to the actual output dimension, not the legacy 1536. Evaluate retrieval on Vietnamese and English course questions. Jina's free tokens are described for non-commercial prototype use; confirm account/model licensing before any commercial deployment.

## Consequences

Store provider, model/version identifier, dimensions, task settings and preprocessing version with each source revision. Store vectors in the selected vector database's native type. Never mix query and document embeddings from different model versions in one retrieval space. A model upgrade requires a new index or indexed revision and a controlled re-embedding plan; provider-neutral code does not eliminate this data migration.

## Revisit when

Revisit if evaluation shows poor Vietnamese retrieval, the provider changes model lifecycle or region support, or embedding cost exceeds the approved budget.

## References

- [Current backend database design](../../backend/DATABASE.md) (legacy AI tables; no fixed embedding dimension contract)
- [Vertex AI text embeddings](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings)
- [Gemini Embedding model notes](https://ai.google.dev/gemini-api/docs/embeddings)
- [Jina v5 text small model and dimensions](https://jina.ai/models/jina-embeddings-v5-text-small/)
- [Jina Embeddings API](https://docs.jina.ai/)
- [Jina embedding plans and free-tier terms](https://jina.ai/embeddings/)
