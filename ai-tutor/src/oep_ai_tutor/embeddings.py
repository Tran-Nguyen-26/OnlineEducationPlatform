"""Provider-neutral text embeddings for indexing and retrieval.

The caller supplies only passages or a query; provider-specific task modes stay here.
An embedding space must be re-indexed when provider, model, dimension or task mode changes.
"""

from __future__ import annotations

from dataclasses import dataclass
import math
import os
import re
from typing import Protocol

import httpx


class EmbeddingProviderError(RuntimeError):
    """A provider failed or returned vectors unsafe to store or search."""


class EmbeddingProvider(Protocol):
    model: str
    dimension: int

    def embed_passages(self, passages: list[str]) -> list[list[float]]: ...

    def embed_query(self, query: str) -> list[float]: ...


def _check_texts(texts: list[str]) -> None:
    if not texts or any(not isinstance(text, str) or not text.strip() for text in texts):
        raise ValueError("Embedding input must contain nonblank text")


def _vector(value: object, dimension: int) -> list[float]:
    if not isinstance(value, list) or len(value) != dimension:
        raise EmbeddingProviderError("Embedding dimension mismatch")
    numbers: list[float] = []
    for number in value:
        if isinstance(number, bool) or not isinstance(number, (int, float)):
            raise EmbeddingProviderError("Embedding contains an invalid number")
        try:
            converted = float(number)
        except OverflowError as exc:
            raise EmbeddingProviderError("Embedding contains an invalid number") from exc
        if not math.isfinite(converted):
            raise EmbeddingProviderError("Embedding contains an invalid number")
        numbers.append(converted)
    return numbers


def _indexed_vectors(payload: object, count: int, dimension: int) -> list[list[float]]:
    if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
        raise EmbeddingProviderError("Malformed embedding response")
    vectors: list[list[float] | None] = [None] * count
    for entry in payload["data"]:
        if not isinstance(entry, dict):
            raise EmbeddingProviderError("Malformed embedding response")
        index = entry.get("index")
        if isinstance(index, bool) or not isinstance(index, int) or not 0 <= index < count:
            raise EmbeddingProviderError("Embedding response index is invalid")
        if vectors[index] is not None:
            raise EmbeddingProviderError("Duplicate embedding response index")
        vectors[index] = _vector(entry.get("embedding"), dimension)
    if any(vector is None for vector in vectors):
        raise EmbeddingProviderError("Embedding response is incomplete")
    return [vector for vector in vectors if vector is not None]


@dataclass
class _HttpEmbeddingProvider:
    model: str
    dimension: int
    api_key: str
    client: httpx.Client

    def __post_init__(self) -> None:
        if not self.model or not self.api_key or self.dimension <= 0:
            raise ValueError("Embedding model, API key and positive dimension are required")

    def _post(self, url: str, body: dict, *, headers: dict[str, str]) -> dict:
        try:
            response = self.client.post(url, json=body, headers=headers)
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise EmbeddingProviderError("Embedding provider unavailable or returned invalid JSON") from exc
        if not isinstance(payload, dict):
            raise EmbeddingProviderError("Malformed embedding response")
        return payload


class JinaEmbeddingProvider(_HttpEmbeddingProvider):
    """Jina v5 retrieval uses different passage and query task adapters."""

    def _embed(self, texts: list[str], task: str) -> list[list[float]]:
        _check_texts(texts)
        payload = self._post(
            "https://api.jina.ai/v1/embeddings",
            {"model": self.model, "task": task, "dimensions": self.dimension,
             "input": texts},
            headers={"Authorization": f"Bearer {self.api_key}"},
        )
        return _indexed_vectors(payload, len(texts), self.dimension)

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        return self._embed(passages, "retrieval.passage")

    def embed_query(self, query: str) -> list[float]:
        return self._embed([query], "retrieval.query")[0]


class OpenAIEmbeddingProvider(_HttpEmbeddingProvider):
    def _embed(self, texts: list[str]) -> list[list[float]]:
        _check_texts(texts)
        payload = self._post(
            "https://api.openai.com/v1/embeddings",
            {"model": self.model, "dimensions": self.dimension,
             "encoding_format": "float", "input": texts},
            headers={"Authorization": f"Bearer {self.api_key}"},
        )
        return _indexed_vectors(payload, len(texts), self.dimension)

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        return self._embed(passages)

    def embed_query(self, query: str) -> list[float]:
        return self._embed([query])[0]


class GeminiEmbeddingProvider(_HttpEmbeddingProvider):
    def __post_init__(self) -> None:
        super().__post_init__()
        if re.fullmatch(r"[A-Za-z0-9._-]+", self.model) is None:
            raise ValueError("Invalid Gemini embedding model name")

    def _embed(self, text: str, task: str) -> list[float]:
        _check_texts([text])
        payload = self._post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:embedContent",
            {"model": f"models/{self.model}",
             "content": {"parts": [{"text": text}]},
             "taskType": task, "outputDimensionality": self.dimension},
            headers={"x-goog-api-key": self.api_key},
        )
        embedding = payload.get("embedding")
        return _vector(embedding.get("values") if isinstance(embedding, dict) else None,
                       self.dimension)

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        _check_texts(passages)
        return [self._embed(text, "RETRIEVAL_DOCUMENT") for text in passages]

    def embed_query(self, query: str) -> list[float]:
        return self._embed(query, "RETRIEVAL_QUERY")


def embedding_provider_from_env(client: httpx.Client) -> EmbeddingProvider:
    """Select one adapter without branching in indexing or retrieval code."""
    name = os.environ.get("AI_TUTOR_EMBEDDING_PROVIDER", "jina").lower()
    defaults = {"jina": ("jina-embeddings-v5-text-small", 1024),
                "openai": ("text-embedding-3-small", 1536),
                "gemini": ("gemini-embedding-001", 3072)}
    constructors = {"jina": (JinaEmbeddingProvider, "JINA_API_KEY"),
                    "openai": (OpenAIEmbeddingProvider, "OPENAI_API_KEY"),
                    "gemini": (GeminiEmbeddingProvider, "GEMINI_API_KEY")}
    if name not in constructors:
        raise ValueError("Unsupported embedding provider")
    model = os.environ.get("AI_TUTOR_EMBEDDING_MODEL", defaults[name][0])
    dimension = int(os.environ.get("AI_TUTOR_EMBEDDING_DIMENSION", defaults[name][1]))
    constructor, key_name = constructors[name]
    return constructor(model, dimension, os.environ.get(key_name, ""), client)
