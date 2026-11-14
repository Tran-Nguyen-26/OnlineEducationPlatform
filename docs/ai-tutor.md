# AI Tutor implementation notes

## Learner flow

- `POST /api/v1/ai/tutor/ask` accepts a course, unlocked lesson, playback timestamp, optional conversation ID, locale (`vi-VN` or `en-US`), and a question up to 500 characters.
- The backend verifies an active learner enrollment, lesson unlock state, access period, and absence of any in-progress quiz before using the daily quota.
- A learner may submit at most 20 questions per Vietnam calendar day. Conversation history is limited to the latest five user/assistant exchanges.
- Retrieval is restricted to the requested lesson. It ranks indexed `ai_document_chunks`, available lesson text, and matching VTT/SRT cues within 90 seconds of the playback timestamp.
- External prompts exclude learner profile identifiers, redact email/phone/IP patterns, and XML-escape user and source content. The system prompt treats both as untrusted data and requires Socratic guidance.
- If no provider is configured or it fails, the response contains the three best matching internal excerpts and timestamp citations.
- Instructor-verified lesson Q&A pairs are indexed in a separate course/lesson-scoped semantic table. The learner discussion composer also suggests up to three verified answers while typing; full-text ranking is used until embeddings are available.
- Course analytics includes an anonymous 30-second rewind heatmap and a misconception list based on incorrect distractor selections. Rewinds are recorded only for backward seeks of at least 10 seconds during a valid active playback session.
- Learners with a measured LO score below 70% receive recommendations for available, unlocked videos tagged to that LO and up to five due single-choice/true-false micro-drills per weak LO. The scheduler uses FSRS-4.5 default weights to update stability, difficulty and the 90% retention interval; wrong answers return in a 10-minute relearning step. Apply migration V33 for per-enrollment review state.
- The AI Tutor panel allows learners to select Vietnamese or English; provider answers, empty-retrieval responses, and retrieval-only fallback follow that selection.

## Admin AI metrics

- Admins can view daily and per-course assistant-answer counts, estimated prompt/completion tokens, average latency, and refusal rate at `/admin/ai-metrics`.
- `GET /api/v1/admin/ai/metrics?from=YYYY-MM-DD&to=YYYY-MM-DD` is admin-only and defaults to the most recent 30 days.
- Metrics cover persisted assistant messages in AI Tutor conversations. Token values are estimates from text length until provider usage reporting is connected; mistake-cache explanations are not included in this dashboard yet.

## Optional OpenAI-compatible provider

Configure `AI_CHAT_COMPLETIONS_URL`, `AI_API_KEY`, and `AI_MODEL` for Core LMS. The URL should point to a chat-completions endpoint. Calls use a 2 second connection timeout and a 10 second read timeout. Configure `AI_EMBEDDINGS_URL` and `AI_EMBEDDING_MODEL` to enable semantic ranking; vectors are indexed in batches of 32. Without a chat provider, retrieval-only responses remain available and the assignment Viva Voce uses text-based fallback questions.

## Assignment Viva Voce

- On essay submission, the platform creates two short questions grounded in the submitted text. Learners submit one response to each question at `/api/v1/courses/{course}/learner-assignments/{assignment}/viva/answers`.
- The instructor sees the questions, answers and optional 0–100 concept-consistency signal in the blind grading claim. The signal evaluates conceptual alignment, is explicitly advisory, and never declares authorship or misconduct.
- When the provider is disabled or the exam circuit breaker is active, text-based questions remain available and the consistency score is left unassessed. Migration V30 adds the persistence fields.

## Current boundary

The tutor currently reads existing `ai_document_chunks`, inline lesson text, and VTT/SRT objects. Automatic PDF/DOCX extraction and embedding generation/vector ranking remain separate ingestion work. Wrong-option explanations are cached by question, selected option, and prompt version; cache hits do not call the LLM. Token counts persisted for cost reporting are estimates based on text length until provider usage reporting is connected.
