"""Deterministic extraction of supported course material with citation provenance."""

from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
import json
import re
from pathlib import PurePath

from pypdf import PdfReader
from pypdf.errors import PdfReadError


@dataclass(frozen=True)
class Passage:
    text: str
    page_number: int | None = None
    heading: str | None = None
    json_path: str | None = None


@dataclass(frozen=True)
class Chunk:
    index: int
    text: str
    page_number: int | None = None
    heading: str | None = None
    json_path: str | None = None


class UnsupportedSource(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def parse_source(content_type: str, payload: str | bytes, *,
                 file_name: str | None = None, mime_type: str | None = None) -> list[Passage]:
    """Parse M2 TEXT/PDF/FILE metadata; eligibility and ownership are checked upstream."""
    if content_type == "TEXT":
        if not isinstance(payload, str):
            raise UnsupportedSource("INVALID_TEXT")
        passages = [Passage(normalize_text(part)) for part in re.split(r"\n\s*\n", payload)]
        passages = [part for part in passages if part.text]
        if not passages:
            raise UnsupportedSource("EMPTY_TEXT")
        return passages

    if not isinstance(payload, bytes) or not file_name or not mime_type:
        raise UnsupportedSource("INVALID_FILE_METADATA")
    suffix = PurePath(file_name).suffix.lower()
    mime = mime_type.split(";", 1)[0].strip().lower()
    if content_type == "PDF":
        if suffix != ".pdf" or mime != "application/pdf":
            raise UnsupportedSource("UNSUPPORTED_FORMAT")
        return parse_pdf(payload)
    if content_type == "FILE" and suffix == ".md" and mime in {
        "text/markdown", "text/x-markdown", "text/plain",
    }:
        return parse_markdown(decode_utf8(payload))
    if content_type == "FILE" and suffix == ".json" and mime == "application/json":
        return parse_json(decode_utf8(payload))
    raise UnsupportedSource("UNSUPPORTED_FORMAT")


def normalize_text(value: str) -> str:
    return " ".join(value.split())


def decode_utf8(payload: bytes) -> str:
    try:
        return payload.decode("utf-8-sig")
    except UnicodeError as exc:
        raise UnsupportedSource("INVALID_UTF8") from exc


def parse_pdf(payload: bytes) -> list[Passage]:
    try:
        reader = PdfReader(BytesIO(payload), strict=True)
        if reader.is_encrypted:
            raise UnsupportedSource("ENCRYPTED_PDF")
        passages = [Passage(normalize_text(page.extract_text() or ""), page_number=index)
                    for index, page in enumerate(reader.pages, start=1)]
    except UnsupportedSource:
        raise
    except (PdfReadError, ValueError, KeyError) as exc:
        raise UnsupportedSource("INVALID_PDF") from exc
    passages = [part for part in passages if part.text]
    if not passages:
        raise UnsupportedSource("OCR_REQUIRED")
    return passages


def parse_markdown(text: str) -> list[Passage]:
    heading_pattern = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
    headings: list[str] = []
    lines: list[str] = []
    sections: list[Passage] = []
    in_fence = False

    def flush() -> None:
        content = "\n".join(lines).strip()
        if content:
            heading = " > ".join(headings) or None
            if heading is not None and len(heading) > 255:
                raise UnsupportedSource("HEADING_TOO_LONG")
            sections.append(Passage(content, heading=heading))
        lines.clear()

    for line in text.splitlines():
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
        match = None if in_fence else heading_pattern.match(line)
        if match:
            flush()
            level = len(match.group(1))
            headings[:] = headings[:level - 1]
            headings.append(match.group(2).strip())
            lines.append(line)
        else:
            lines.append(line)
    flush()
    if not sections:
        raise UnsupportedSource("EMPTY_MARKDOWN")
    return sections


def parse_json(text: str) -> list[Passage]:
    try:
        data = json.loads(text)
    except (json.JSONDecodeError, RecursionError) as exc:
        raise UnsupportedSource("INVALID_JSON") from exc
    passages: list[Passage] = []

    def walk(value: object, path: str, depth: int) -> None:
        if depth > 32:
            raise UnsupportedSource("JSON_TOO_DEEP")
        if isinstance(value, dict):
            for key, child in value.items():
                escaped = key.replace("~", "~0").replace("/", "~1")
                walk(child, path + "/" + escaped, depth + 1)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                walk(child, path + "/" + str(index), depth + 1)
        else:
            if len(path) > 500:
                raise UnsupportedSource("JSON_PATH_TOO_LONG")
            rendered = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            passages.append(Passage(f"{path}: {rendered}", json_path=path))

    walk(data, "$", 0)
    if not passages:
        raise UnsupportedSource("EMPTY_JSON")
    return passages


def chunk_passages(passages: list[Passage], *, max_chars: int = 3000,
                   overlap_chars: int = 300) -> list[Chunk]:
    """Bound each passage without merging citation locations across pages or paths."""
    if max_chars < 2 or not 0 <= overlap_chars < max_chars // 2:
        raise ValueError("Invalid chunk window")
    chunks: list[Chunk] = []
    for passage in passages:
        text = passage.text.strip()
        if not text:
            continue
        start = 0
        while start < len(text):
            end = min(start + max_chars, len(text))
            if end < len(text):
                boundary = text.rfind(" ", start + max_chars // 2, end + 1)
                if boundary > start:
                    end = boundary
            piece = text[start:end].strip()
            if piece:
                chunks.append(Chunk(len(chunks), piece, passage.page_number,
                                    passage.heading, passage.json_path))
            if end == len(text):
                break
            start = max(start + 1, end - overlap_chars)
    return chunks
