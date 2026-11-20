"""Database access shared by private M7 handlers."""

from __future__ import annotations

from contextlib import contextmanager
from typing import Iterator

import psycopg
from psycopg.rows import dict_row
from fastapi import Request

from oep_ai_tutor.errors import AiTutorError


@contextmanager
def database(request: Request) -> Iterator[psycopg.Connection]:
    dsn = request.app.state.database_url
    if not dsn:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "AI Tutor is unavailable")
    try:
        with psycopg.connect(dsn, row_factory=dict_row) as connection:
            yield connection
    except psycopg.Error as exc:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "AI Tutor is unavailable") from exc
