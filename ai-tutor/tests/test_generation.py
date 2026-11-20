"""Completed-response generation contracts, tested without live API keys."""

from __future__ import annotations

import json

import httpx
import pytest

from oep_ai_tutor.generation import (
    GenerationMessage,
    GenerationProviderError,
    GeminiGenerationProvider,
    GroqGenerationProvider,
    OpenAIGenerationProvider,
    generation_provider_from_env,
)


def _client(handler):
    return httpx.Client(transport=httpx.MockTransport(handler), timeout=2)


@pytest.mark.parametrize("provider_type,endpoint,role", [
    (GroqGenerationProvider, "https://api.groq.com/openai/v1/chat/completions", "system"),
    (OpenAIGenerationProvider, "https://api.openai.com/v1/chat/completions", "developer"),
])
def test_chat_completion_adapters_keep_instructions_separate_and_normalize_usage(
    provider_type, endpoint, role,
) -> None:
    calls = []

    def respond(request):
        assert str(request.url) == endpoint
        assert request.headers["authorization"] == "Bearer secret"
        calls.append(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{
            "finish_reason": "stop", "message": {"role": "assistant", "content": "  Answer  "}}],
            "usage": {"prompt_tokens": 12, "completion_tokens": 4}})

    with _client(respond) as client:
        provider = provider_type("model-id", "secret", client, 128)
        result = provider.generate("Follow course evidence only", [
            GenerationMessage("user", "Earlier question"),
            GenerationMessage("assistant", "Earlier answer"),
            GenerationMessage("user", "Current question and untrusted excerpts"),
        ])
    assert (result.text, result.input_tokens, result.output_tokens, result.model) == (
        "Answer", 12, 4, "model-id")
    assert calls[0]["messages"][0] == {"role": role, "content": "Follow course evidence only"}
    assert [message["role"] for message in calls[0]["messages"][1:]] == [
        "user", "assistant", "user"]
    assert calls[0]["max_completion_tokens"] == 128
    assert calls[0]["stream"] is False


def test_gemini_maps_history_and_usage() -> None:
    bodies = []

    def respond(request):
        assert request.url.path == "/v1beta/models/gemini-2.5-flash:generateContent"
        assert request.headers["x-goog-api-key"] == "secret"
        bodies.append(json.loads(request.content))
        return httpx.Response(200, json={
            "candidates": [{"finishReason": "STOP", "content": {"parts": [
                {"text": "First "}, {"text": "second"}]}}],
            "usageMetadata": {"promptTokenCount": 18, "candidatesTokenCount": 3}})

    with _client(respond) as client:
        provider = GeminiGenerationProvider("gemini-2.5-flash", "secret", client)
        result = provider.generate("System rules", [GenerationMessage("user", "Question"),
                                                   GenerationMessage("assistant", "Prior answer"),
                                                   GenerationMessage("user", "Follow-up")])
    assert (result.text, result.input_tokens, result.output_tokens) == ("First second", 18, 3)
    assert bodies[0]["systemInstruction"] == {"parts": [{"text": "System rules"}]}
    assert [content["role"] for content in bodies[0]["contents"]] == ["user", "model", "user"]
    assert bodies[0]["generationConfig"]["maxOutputTokens"] == 1024


@pytest.mark.parametrize("payload", [
    {"choices": []},
    {"choices": [{"finish_reason": "length", "message": {"content": "Truncated"}}]},
    {"choices": [{"finish_reason": "stop", "message": {"content": " "}}]},
    {"choices": [{"finish_reason": "stop", "message": {"content": "Answer"}}],
     "usage": {"prompt_tokens": -1}},
])
def test_rejects_unusable_chat_completion(payload) -> None:
    with _client(lambda request: httpx.Response(200, json=payload)) as client:
        provider = GroqGenerationProvider("model", "secret", client)
        with pytest.raises(GenerationProviderError):
            provider.generate("Rules", [GenerationMessage("user", "Question")])


def test_rejects_blocked_gemini_response_and_provider_error() -> None:
    with _client(lambda request: httpx.Response(200, json={"candidates": []})) as client:
        provider = GeminiGenerationProvider("gemini-2.5-flash", "secret", client)
        with pytest.raises(GenerationProviderError):
            provider.generate("Rules", [GenerationMessage("user", "Question")])
    with _client(lambda request: httpx.Response(429, json={"error": "private detail"})) as client:
        provider = GeminiGenerationProvider("gemini-2.5-flash", "secret", client)
        with pytest.raises(GenerationProviderError, match="provider unavailable") as error:
            provider.generate("Rules", [GenerationMessage("user", "Question")])
    assert "private detail" not in str(error.value)


def test_prompt_validation_and_provider_selection(monkeypatch) -> None:
    with pytest.raises(ValueError):
        GenerationMessage("system", "Never allowed")
    with _client(lambda request: httpx.Response(200)) as client:
        provider = GroqGenerationProvider("model", "secret", client)
        with pytest.raises(ValueError):
            provider.generate("Rules", [GenerationMessage("assistant", "Wrong last role")])
        monkeypatch.setenv("GROQ_API_KEY", "secret")
        monkeypatch.delenv("AI_TUTOR_GENERATION_PROVIDER", raising=False)
        default = generation_provider_from_env(client)
        assert isinstance(default, GroqGenerationProvider)
        assert default.model == "openai/gpt-oss-120b"
        monkeypatch.setenv("AI_TUTOR_GENERATION_PROVIDER", "openai")
        monkeypatch.setenv("AI_TUTOR_GENERATION_MODEL", "configured-model")
        monkeypatch.setenv("OPENAI_API_KEY", "secret")
        assert isinstance(generation_provider_from_env(client), OpenAIGenerationProvider)
        monkeypatch.setenv("AI_TUTOR_GENERATION_PROVIDER", "gemini")
        monkeypatch.setenv("GEMINI_API_KEY", "secret")
        assert isinstance(generation_provider_from_env(client), GeminiGenerationProvider)
