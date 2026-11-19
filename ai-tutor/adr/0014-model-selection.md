# ADR-AI-001: LLM and embedding model comparison

- **Status:** Initial Groq/Jina configuration accepted; historical shortlist retained for later evaluation
- **Research date:** 2026-10-24
- **Decision owner(s):** M7 project team
- **Related ADRs:** [0002 Embedding model and dimensions](0002-embedding-model-and-dimensions.md), [0005 Answer-generation model and provider](0005-generation-model-provider.md), [0004 Retrieval and ranking](0004-retrieval-and-ranking.md)

## Context

The original M7 checklist called for comparing two generation and two embedding models on quality, cost and latency. The old schema proposed 1536-dimensional embeddings, but the selected embedding model must determine the actual index dimension. Retrieval quality must be measured on the project's Vietnamese/English course material rather than inferred from vendor-wide benchmark scores.

**Confirmed first-release configuration (2026-11-19):** start generation with Groq `openai/gpt-oss-120b`; implement Groq, OpenAI and Gemini generation adapters behind one interface. Start embeddings with **Jina AI `jina-embeddings-v5-text-small`** at 1024 dimensions for text extracted from PDF/TEXT/MD/JSON; implement Jina, OpenAI and Gemini embedding adapters. pgvector is the temporary vector-store default with a Pinecone adapter also required. The tables below are an **older research snapshot**, not the selected configuration or current price quote. Recheck vendor pages and run the PoC before budget approval.

## Decision drivers

- Vietnamese and English answer quality, groundedness, citation adherence and refusal behavior.
- Retrieval quality at the selected model's actual output dimension on the same fixed PoC set.
- Published input/output or embedding price per 1M tokens.
- Measured end-to-end and first-token latency under the project's prompt and context limits.
- Streaming support, stable model identifiers, data handling and the documented GCP deployment context.

## Historical generation model comparison (not current selection)

Prices below are USD per 1M tokens for the standard paid API tier, read on 2026-10-24. They do not include retries, caching, taxes or account-specific discounts. Published prices are not a substitute for measuring this workload.

| Model | Provider / API | Input / output price | Published quality evidence | Latency | Notes |
| --- | --- | ---: | --- | --- | --- |
| `gpt-6-luna` | OpenAI API | $0.10 / $0.50 | Provider describes it as its efficient model for focused, high-volume workloads; no project-specific Vietnamese RAG score is published. | No comparable vendor p95 for this workload; measure first-token and full-answer latency in PoC. | Strong low-cost candidate. Supports 1.05M context window; the application should still enforce its own 4,000-token context cap. |
| `gemini-3.8-flash` | Gemini Developer API | $0.75 / $3.75 through 2026-12-31; $1.50 / $7.50 starting 2027-01-01 | Provider describes it as its most intelligent Flash model for long-horizon and complex tasks; no project-specific Vietnamese RAG score is published. | No comparable vendor p95 for this workload; measure first-token and full-answer latency in PoC. | Supports streaming generation. Check the announced price increase when setting the budget. |

**Illustrative generation cost:** for 1,000 questions averaging 3,000 input tokens and 500 output tokens each, estimated token charges are $0.55 for GPT-6 Luna and $4.125 for Gemini 3.8 Flash at its 2026 rate; Gemini's same workload becomes $8.25 at the stated 2027 rate. This is arithmetic from published list prices, not a measured usage forecast. Actual tokenization, reasoning tokens and retries can change it.

No reliable apples-to-apples latency or Vietnamese answer-quality figures were found in the primary model documentation. Record observed p50/p95 and evaluation results from the PoC instead of filling those cells with unsupported estimates.

## Historical embedding model comparison (not current selection)

Prices are USD per 1M input tokens on the standard paid tier, read on 2026-10-24. The historical candidates below can produce 1536 dimensions; the current embedding choice is not fixed to that dimension. Embeddings from different model families are incompatible; index and query vectors must use the same model/version and compatible task settings.

| Model | Provider / API | 1536 dimensions | Price / 1M input tokens | Published quality evidence | Latency |
| --- | --- | --- | ---: | --- | --- |
| `text-embedding-3-small` | OpenAI API | Default output is 1536 | $0.02 | Vendor reports MTEB 62.3%; benchmark is not a substitute for this course dataset. | No comparable published p95; measure batch and single-query latency. |
| `text-embedding-3-large` | OpenAI API | Request 1536 with the `dimensions` parameter (default is 3072) | $0.13 | Vendor reports MTEB 64.6%; benchmark is not a substitute for this course dataset. | No comparable published p95; measure batch and single-query latency. |
| `gemini-embedding-2` | Gemini Developer API | Configurable; 1536 is a recommended output size | $0.20 (text); $0.10 via Batch API | Provider documents multilingual and multimodal support; no directly comparable score for the project corpus. | No comparable published p95; measure batch and single-query latency. |

Provider MTEB figures are presented only for models where the vendor publishes them. They may use different evaluation setups and must not be treated as a direct ranking across providers. `gemini-embedding-001` is not shortlisted: Google has published its deprecation date as 2028-05-14 and recommends migration to `gemini-embedding-2`.

## Accepted initial configuration and later evaluation

1. Use Groq `openai/gpt-oss-120b` as the first generation configuration, while delivering Groq/OpenAI/Gemini adapters so another provider/model can be selected by configuration. Compare their answer correctness, groundedness, citation validity, refusal behavior, latency and token use in the later evaluation phase.
2. Start the Jina adapter with `jina-embeddings-v5-text-small` at 1024 dimensions, using passage/query retrieval modes; test it against at least one alternative on the same PDF/chunks and questions. Measure retrieval quality, latency and cost. Configure the vector index to the chosen dimension instead of forcing 1536.
3. Reassess the production provider and vector store after the PoC, privacy/data-handling review and measured latency/cost. The cheapest token price alone is insufficient.
4. Use the same chosen embedding model/version for both indexing and question vectors. Switching the embedding space requires re-embedding all indexed content; switching only the generation model does not.

## Consequences

- The legacy 1536-dimensional storage proposal is no longer a fixed contract; the chosen model/index dimension must match.
- The price comparison is a dated snapshot and must be refreshed before an implementation commitment.
- The model selection PoC must capture exact provider model IDs, date, request configuration, question set, outputs, token counts and observed latency so another team member can reproduce it.
- A provider's published benchmark or model description is not evidence that the M7 acceptance targets have been met.

## Revisit when

Revisit after the retrieval/generation PoC, if list prices or model status change, or when data residency, provider terms or deployment constraints are clarified.

## References

- [OpenAI model catalog and current model prices](https://developers.openai.com/api/docs/models)
- [Gemini 3.8 Flash model guide](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash)
- [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini streaming API (SSE)](https://ai.google.dev/api/generate-content#method:-models.streamGenerateContent)
- [OpenAI text-embedding-3-small model and price](https://developers.openai.com/api/docs/models/text-embedding-3-small)
- [OpenAI embeddings guide: dimensions and vendor MTEB figures](https://developers.openai.com/api/docs/guides/embeddings)
- [OpenAI embeddings API: `dimensions` parameter](https://developers.openai.com/api/reference/resources/embeddings/methods/create)
- [Gemini Embedding 2 dimensions](https://ai.google.dev/gemini-api/docs/embeddings)
- [Gemini embedding pricing](https://ai.google.dev/gemini-api/docs/pricing#gemini-embedding-2)
- [Gemini deprecations](https://ai.google.dev/gemini-api/docs/deprecations)
- [Jina v5 text small model](https://jina.ai/models/jina-embeddings-v5-text-small/)
