# ADR-0001: Vector database and storage boundary

- **Status:** Accepted for first release; pgvector default is temporary
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7/M2 project team
- **Related ADRs:** [0002 Embeddings](0002-embedding-model-and-dimensions.md), [0004 Retrieval](0004-retrieval-and-ranking.md)

## Context

The repository contains conflicting architecture directions. `backend/CLAUDE.md` and `backend/DATABASE.md` depict Pinecone, while earlier M7 drafts considered pgvector in PostgreSQL. The current `AiDocumentChunk` entity stores `embedding_vector` as `text`; it does not implement either vector index. The team initially leaned toward Pinecone and requested a recommendation. The project already runs PostgreSQL locally and its applied AWS demo is in `ap-southeast-2`.

## Decision drivers

- Keep course filtering enforceable on every search.
- Keep MVP deployment and operations manageable.
- Avoid degrading LMS database availability or latency.
- Support expected document/chunk volume and future growth.
- Keep embedding dimensions and model migrations explicit.

## Options considered

### Option A: PostgreSQL with pgvector

- Benefits: reuses the existing PostgreSQL platform; stores source metadata, chunks and vectors together; fewer services to operate.
- Benefits: SQL joins and transactional source-state checks stay near the vectors; no cross-store synchronization for version activation/deletion. pgvector supports exact nearest-neighbor search and HNSW when scale calls for it.
- Costs and risks: vector work shares database resources with LMS workloads; approximate-index filtering can return fewer results unless scan settings or query strategy account for filters; schema and extension support must be verified in the target environment. The team operates indexing, tuning and capacity.

### Option B: Pinecone managed vector database

- Benefits: separates vector search capacity from the relational LMS database; managed serverless vector operations, metadata filters and a Starter tier for prototyping. The team avoids pgvector extension/index operations in its PostgreSQL service.
- Costs and risks: adds a managed service, credentials, network hop, usage/region limits and data synchronization; deletion and version activation must coordinate across two stores. The free Starter tier is limited to AWS us-east-1, so check data-region requirements before relying on it.

## Proposal or decision

**Accepted for the first release:** use pgvector as the temporary deployment default. M7 already needs PostgreSQL for chat, source revisions and jobs; keeping vectors with source state simplifies the required deletion/replacement handshake and course/version filtering. **Implement both** `PgvectorStore` and `PineconeStore` behind a store-neutral `VectorStore` interface for upsert, course-filtered query, revision deactivation/delete and health. Select the adapter by configuration; do not branch RAG/chat or HTTP contracts on a store name. Use an M7-owned schema/role and a native pgvector `vector` column, storing an explicit embedding-space key and dimension with each revision/chunk. The initial active space uses Jina's 1024 dimensions; its index and query operate only within that space. Build a new dimension-specific index and re-embed when the space changes. For Pinecone, use a bring-your-own-vectors index so Jina stays independent of Pinecone inference; create a new index when dimensions change. Run shared isolation/version/deletion contract tests against both. Changing stores still requires vector migration or re-indexing.

## Consequences

For pgvector, use a real `vector` column and a migration that enables the extension; do not serialize vectors into `text`. Create expression/partial vector indexes for each active homogeneous embedding space and reject mixed-space comparisons. For Pinecone, define stable IDs, namespace/filter strategy, region and M2 deletion synchronization. In both cases apply the verified `course_id` inside search and recheck current source eligibility before answer generation. Switching stores requires data migration/reindexing even though the application interface stays stable.

## Revisit when

Revisit after the target cloud database is selected or when staging load shows that vector queries affect LMS workloads.

## References

- [Project architecture](../../backend/CLAUDE.md)
- [Current backend database design](../../backend/DATABASE.md)
- [Current M7 design](../docs/m7-ai-tutor.md)
- [Google Cloud SQL supported extensions](https://docs.cloud.google.com/sql/docs/postgres/extensions)
- [pgvector filtering and iterative scans](https://github.com/pgvector/pgvector/blob/master/README.md#filtering)
- [Pinecone production checklist](https://docs.pinecone.io/guides/operations/moving-to-production)
- [Pinecone current plans and Starter limits](https://www.pinecone.io/pricing/)
- [AWS RDS PostgreSQL extension support](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-extensions.html)
