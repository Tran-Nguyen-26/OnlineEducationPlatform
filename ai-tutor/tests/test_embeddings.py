"""Provider HTTP contracts and shared vector validation without live credentials."""

from __future__ import annotations

import json
import math

import httpx
import pytest

from oep_ai_tutor.embeddings import (
    EmbeddingProviderError,
    GeminiEmbeddingProvider,
    JinaEmbeddingProvider,
    OpenAIEmbeddingProvider,
    embedding_provider_from_env,
)


def _client(handler):
    return httpx.Client(transport=httpx.MockTransport(handler), timeout=2)


def test_jina_uses_distinct_retrieval_tasks_and_reorders_batch() -> None:
    requests = []

    def respond(request):
        body = json.loads(request.content)
        requests.append((request, body))
        assert request.headers["authorization"] == "Bearer secret"
        assert body["dimensions"] == 3
        data = [{"index": index, "embedding": [float(index + 1), 0, 0]}
                for index in reversed(range(len(body["input"])))]
        return httpx.Response(200, json={"data": data})

    with _client(respond) as client:
        provider = JinaEmbeddingProvider("jina-embeddings-v5-text-small", 3, "secret", client)
        assert provider.embed_passages(["first", "second"]) == [[1.0, 0.0, 0.0], [2.0, 0.0, 0.0]]
        assert provider.embed_query("question") == [1.0, 0.0, 0.0]
    assert [body["task"] for _, body in requests] == ["retrieval.passage", "retrieval.query"]
    assert all(str(request.url) == "https://api.jina.ai/v1/embeddings" for request, _ in requests)


def test_openai_uses_one_batch_and_requested_dimension() -> None:
    calls = []

    def respond(request):
        body = json.loads(request.content)
        calls.append(body)
        return httpx.Response(200, json={"data": [
            {"index": index, "embedding": [0.1, 0.2, 0.3]}
            for index in range(len(body["input"]))]})

    with _client(respond) as client:
        provider = OpenAIEmbeddingProvider("text-embedding-3-small", 3, "secret", client)
        assert len(provider.embed_passages(["one", "two"])) == 2
        assert provider.embed_query("question") == [0.1, 0.2, 0.3]
    assert calls[0] == {"model": "text-embedding-3-small", "dimensions": 3,
                        "encoding_format": "float", "input": ["one", "two"]}


def test_gemini_uses_document_and_query_task_modes() -> None:
    tasks = []

    def respond(request):
        body = json.loads(request.content)
        assert request.headers["x-goog-api-key"] == "secret"
        assert request.url.path == "/v1beta/models/gemini-embedding-001:embedContent"
        assert body["outputDimensionality"] == 3
        tasks.append(body["taskType"])
        return httpx.Response(200, json={"embedding": {"values": [0, 1, 2]}})

    with _client(respond) as client:
        provider = GeminiEmbeddingProvider("gemini-embedding-001", 3, "secret", client)
        assert len(provider.embed_passages(["one", "two"])) == 2
        assert provider.embed_query("question") == [0.0, 1.0, 2.0]
    assert tasks == ["RETRIEVAL_DOCUMENT", "RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"]


@pytest.mark.parametrize("data", [
    [{"index": 0, "embedding": [1, 2]}],
    [{"index": 0, "embedding": [1, math.nan, 3]}],
    [{"index": 0, "embedding": [1, True, 3]}],
    [{"index": 0, "embedding": [1, 10 ** 1000, 3]}],
    [{"index": 0, "embedding": [1, 2, 3]}, {"index": 0, "embedding": [1, 2, 3]}],
    [],
])
def test_rejects_bad_vectors_and_incomplete_batches(data) -> None:
    with _client(lambda request: httpx.Response(200, json={"data": data})) as client:
        provider = JinaEmbeddingProvider("model", 3, "secret", client)
        with pytest.raises(EmbeddingProviderError):
            provider.embed_query("question")


def test_provider_errors_are_safe_and_empty_inputs_do_not_call_network() -> None:
    requests = []

    def respond(request):
        requests.append(request)
        return httpx.Response(503, json={"error": "private provider detail"})

    with _client(respond) as client:
        provider = OpenAIEmbeddingProvider("model", 3, "secret", client)
        with pytest.raises(ValueError):
            provider.embed_passages([" "])
        with pytest.raises(EmbeddingProviderError, match="provider unavailable") as error:
            provider.embed_query("question")
    assert "private provider detail" not in str(error.value)
    assert len(requests) == 1


def test_factory_selects_configured_provider_and_rejects_bad_config(monkeypatch) -> None:
    monkeypatch.setenv("JINA_API_KEY", "secret")
    monkeypatch.delenv("AI_TUTOR_EMBEDDING_PROVIDER", raising=False)
    with _client(lambda request: httpx.Response(200)) as client:
        provider = embedding_provider_from_env(client)
        assert isinstance(provider, JinaEmbeddingProvider)
        assert (provider.model, provider.dimension) == ("jina-embeddings-v5-text-small", 1024)
        monkeypatch.setenv("AI_TUTOR_EMBEDDING_PROVIDER", "gemini")
        monkeypatch.setenv("GEMINI_API_KEY", "secret")
        assert isinstance(embedding_provider_from_env(client), GeminiEmbeddingProvider)
        monkeypatch.setenv("AI_TUTOR_EMBEDDING_PROVIDER", "openai")
        monkeypatch.setenv("OPENAI_API_KEY", "secret")
        assert isinstance(embedding_provider_from_env(client), OpenAIEmbeddingProvider)
        monkeypatch.setenv("AI_TUTOR_EMBEDDING_PROVIDER", "unknown")
        with pytest.raises(ValueError, match="Unsupported"):
            embedding_provider_from_env(client)
