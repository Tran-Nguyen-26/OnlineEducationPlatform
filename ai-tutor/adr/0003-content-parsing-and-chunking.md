# ADR-0003: Content parsing and chunking

- **Status:** Accepted format scope; chunking parameters proposed
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7 project team, with M2 content-owner input
- **Related ADRs:** [0002 Embeddings](0002-embedding-model-and-dimensions.md), [0004 Retrieval](0004-retrieval-and-ranking.md)

## Context

M2 supports inline text and file content including PDF, DOCX/document, slides and video. The functional M7 release is requested to support `TEXT`, text-based PDF, Markdown and JSON. M2 has no `MD` or `JSON` content type, but it has a general `FILE` type with file name and MIME metadata. An earlier chunk target of roughly 500–1000 tokens remains unverified against course material. Chunk boundaries affect source citations, retrieval quality and embedding cost.

## Decision drivers

- Preserve lesson, heading, page and content-version provenance for citations.
- Avoid splitting a definition, table or procedure across unrelated chunks.
- Bound embedding input length and keep prompt context manageable.
- Handle Vietnamese and English material consistently.
- Restrict supported formats to parsers the team can validate and operate.

## Options considered

### Option A: Fixed-size token windows

- Benefits: simple, deterministic and easy to estimate for cost.
- Costs and risks: may split headings, tables and semantic units; citations can be less useful.

### Option B: Structure-aware chunks with bounded token size

- Benefits: keeps headings and paragraphs together and supports page-level source metadata.
- Costs and risks: parser-specific; oversized sections still need deterministic splitting and tables need special handling.

### Option C: Recursive/semantic chunking

- Benefits: attempts to preserve coherent semantic sections.
- Costs and risks: more tuning and less predictable chunk sizes; can be harder to reproduce across parser changes.

## Proposal or decision

**Accepted format scope; chunking details proposed:** use deterministic, structure-aware chunking for inline `TEXT`, text PDF, `.md` and `.json`. Preserve PDF page, Markdown heading and JSON path where available alongside lesson/content/version metadata. Flatten scalar leaves of Admin-uploaded learning-content JSON into stable `path: value` passages; do not ingest arbitrary user/account JSON. Map `.md`/`.json` through M2 `FILE` with a MIME/extension allowlist. Start around 500–800 tokens with modest overlap, then measure retrieval quality. DOCX, slides, `LINK` crawling and video transcription are outside this release.

## Consequences

Parser and chunker versions must be recorded on source revisions so re-indexing is reproducible. Chunk IDs are version-specific. Tables, scanned PDFs, footnotes and OCR remain explicit test cases or out of scope until the parser handles them reliably.

## Revisit when

Revisit after retrieval evaluation identifies missed cross-paragraph answers, poor citations, or frequent parser failures.

## References

- [Live M2 lesson-content service](../../backend/core-lms-service/src/main/java/com/oep/corelms/service/AdminLessonContentService.java)
- [Current M7 ingestion proposal](../docs/m7-ai-tutor.md)
