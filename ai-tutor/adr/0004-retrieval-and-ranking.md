# ADR-0004: Retrieval, ranking and abstention

- **Status:** Proposed
- **Decision date:** Pending
- **Decision owner(s):** M7 project team
- **Related ADRs:** [0001 Vector database](0001-vector-database.md), [0002 Embeddings](0002-embedding-model-and-dimensions.md), [0003 Chunking](0003-content-parsing-and-chunking.md)

## Context

M7 must retrieve only authorized, current content for one verified course and cite the retrieved evidence. The current draft suggests dense vector search with initial `K=5`, while the legacy design calls for HNSW and a `(course_id, lesson_id)` B-tree index. Course filtering and abstention thresholds need evaluation, not guesswork.

## Decision drivers

- No cross-course results or stale/deleted sources.
- Good recall for both paraphrased questions and exact technical terms.
- Stable citation support and safe abstention when evidence is weak or contradictory.
- Predictable latency and manageable implementation complexity.

## Options considered

### Option A: Dense vector retrieval only

- Benefits: simplest baseline; good semantic matching.
- Costs and risks: exact names, codes and rare terms can be missed; approximate index filtering may reduce result count.

### Option B: Hybrid dense and lexical retrieval

- Benefits: combines semantic similarity with exact term matching.
- Costs and risks: adds scoring/fusion logic and more parameters to tune.

### Option C: Add a reranker

- Benefits: can reorder a larger candidate set for relevance.
- Costs and risks: extra model call, latency, cost and another provider/model dependency.

## Proposal or decision

**Proposal only:** establish a dense-only baseline. If Pinecone is selected, include the verified `course_id` in the vector query metadata filter and validate returned IDs against current `READY` source versions in M7's relational store before giving text to the model. If pgvector is selected, apply course and source-state predicates in SQL. Compare candidate sizes such as 5 and 10 against a small golden set. Add lexical hybrid retrieval or reranking only if measured quality justifies them. Tune abstention against answerable and unanswerable questions.

## Consequences

Retrieval evaluation must report Recall@K, citation support, abstention precision/recall, latency and cross-course leakage negatives. With Pinecone, two-store source eligibility and deletion need explicit consistency tests. With pgvector HNSW, SQL metadata filters remain mandatory, but approximate search can apply filters after candidate scanning; configure iterative scans or use another measured strategy to avoid under-filled results. A filter-related recall problem is not permission to relax the authorization predicate.

## Revisit when

Revisit after the first course evaluation set or when the corpus grows enough to change exact-versus-approximate search trade-offs.

## References

- [M7 retrieval and answer rules](../docs/m7-ai-tutor.md)
- [pgvector filtering behavior](https://github.com/pgvector/pgvector/blob/master/README.md#filtering)
