"""Provider-neutral completed text generation for grounded chat turns."""

from __future__ import annotations

from dataclasses import dataclass
import os
import re
from typing import Literal, Protocol

import httpx


class GenerationProviderError(RuntimeError):
    """A provider did not return a complete, usable answer."""


@dataclass(frozen=True)
class GenerationMessage:
    role: Literal["user", "assistant"]
    content: str

    def __post_init__(self) -> None:
        if self.role not in ("user", "assistant") or not self.content.strip():
            raise ValueError("Generation messages require a user/assistant role and content")


@dataclass(frozen=True)
class GenerationResult:
    text: str
    input_tokens: int | None
    output_tokens: int | None
    model: str


class GenerationProvider(Protocol):
    model: str

    def generate(self, instructions: str, messages: list[GenerationMessage]) -> GenerationResult: ...


def _usage(value: object) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise GenerationProviderError("Invalid provider usage")
    return value


def _validate_prompt(instructions: str, messages: list[GenerationMessage]) -> None:
    if not isinstance(instructions, str) or not instructions.strip() or not messages:
        raise ValueError("Generation requires instructions and messages")
    if any(not isinstance(message, GenerationMessage) for message in messages):
        raise ValueError("Generation messages are invalid")
    if messages[-1].role != "user":
        raise ValueError("Last generation message must be from the user")


@dataclass
class _HttpGenerationProvider:
    model: str
    api_key: str
    client: httpx.Client
    max_output_tokens: int = 1024

    def __post_init__(self) -> None:
        if not self.model or not self.api_key or self.max_output_tokens <= 0:
            raise ValueError("Generation model, API key and positive output limit are required")

    def _post(self, url: str, body: dict, *, headers: dict[str, str]) -> dict:
        try:
            response = self.client.post(url, json=body, headers=headers)
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise GenerationProviderError("Generation provider unavailable or returned invalid JSON") from exc
        if not isinstance(payload, dict):
            raise GenerationProviderError("Malformed generation response")
        return payload


class _ChatCompletionProvider(_HttpGenerationProvider):
    endpoint: str
    instruction_role: str

    def generate(self, instructions: str, messages: list[GenerationMessage]) -> GenerationResult:
        _validate_prompt(instructions, messages)
        payload = self._post(
            self.endpoint,
            {"model": self.model, "stream": False, "max_completion_tokens": self.max_output_tokens,
             "messages": [{"role": self.instruction_role, "content": instructions}]
             + [{"role": message.role, "content": message.content} for message in messages]},
            headers={"Authorization": f"Bearer {self.api_key}"},
        )
        choices = payload.get("choices")
        if not isinstance(choices, list) or len(choices) != 1 or not isinstance(choices[0], dict):
            raise GenerationProviderError("Malformed generation response")
        choice = choices[0]
        message = choice.get("message")
        text = message.get("content") if isinstance(message, dict) else None
        if choice.get("finish_reason") != "stop" or not isinstance(text, str) or not text.strip():
            raise GenerationProviderError("Generation did not produce a complete answer")
        usage = payload.get("usage")
        if usage is not None and not isinstance(usage, dict):
            raise GenerationProviderError("Invalid provider usage")
        usage = usage or {}
        return GenerationResult(text.strip(), _usage(usage.get("prompt_tokens")),
                                _usage(usage.get("completion_tokens")), self.model)


class GroqGenerationProvider(_ChatCompletionProvider):
    endpoint = "https://api.groq.com/openai/v1/chat/completions"
    instruction_role = "system"


class OpenAIGenerationProvider(_ChatCompletionProvider):
    endpoint = "https://api.openai.com/v1/chat/completions"
    instruction_role = "developer"


class GeminiGenerationProvider(_HttpGenerationProvider):
    def __post_init__(self) -> None:
        super().__post_init__()
        if re.fullmatch(r"[A-Za-z0-9._-]+", self.model) is None:
            raise ValueError("Invalid Gemini generation model name")

    def generate(self, instructions: str, messages: list[GenerationMessage]) -> GenerationResult:
        _validate_prompt(instructions, messages)
        payload = self._post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent",
            {"systemInstruction": {"parts": [{"text": instructions}]},
             "contents": [{"role": "model" if message.role == "assistant" else "user",
                           "parts": [{"text": message.content}]} for message in messages],
             "generationConfig": {"maxOutputTokens": self.max_output_tokens}},
            headers={"x-goog-api-key": self.api_key},
        )
        candidates = payload.get("candidates")
        if not isinstance(candidates, list) or len(candidates) != 1 or not isinstance(candidates[0], dict):
            raise GenerationProviderError("Malformed generation response")
        candidate = candidates[0]
        content = candidate.get("content")
        parts = content.get("parts") if isinstance(content, dict) else None
        if candidate.get("finishReason") != "STOP" or not isinstance(parts, list):
            raise GenerationProviderError("Generation did not produce a complete answer")
        if any(not isinstance(part, dict) or not isinstance(part.get("text"), str) for part in parts):
            raise GenerationProviderError("Malformed generation response")
        text = "".join(part["text"] for part in parts).strip()
        if not text:
            raise GenerationProviderError("Generation did not produce a complete answer")
        usage = payload.get("usageMetadata")
        if usage is not None and not isinstance(usage, dict):
            raise GenerationProviderError("Invalid provider usage")
        usage = usage or {}
        return GenerationResult(text, _usage(usage.get("promptTokenCount")),
                                _usage(usage.get("candidatesTokenCount")), self.model)


def generation_provider_from_env(client: httpx.Client) -> GenerationProvider:
    name = os.environ.get("AI_TUTOR_GENERATION_PROVIDER", "groq").lower()
    constructors = {"groq": (GroqGenerationProvider, "GROQ_API_KEY"),
                    "openai": (OpenAIGenerationProvider, "OPENAI_API_KEY"),
                    "gemini": (GeminiGenerationProvider, "GEMINI_API_KEY")}
    if name not in constructors:
        raise ValueError("Unsupported generation provider")
    model = os.environ.get("AI_TUTOR_GENERATION_MODEL", "openai/gpt-oss-120b" if name == "groq" else "")
    output_limit = int(os.environ.get("AI_TUTOR_MAX_OUTPUT_TOKENS", "1024"))
    constructor, key_name = constructors[name]
    return constructor(model, os.environ.get(key_name, ""), client, output_limit)
