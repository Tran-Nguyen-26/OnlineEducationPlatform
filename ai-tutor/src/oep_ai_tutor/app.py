"""FastAPI entry point for the private M7 service."""

from __future__ import annotations

import os

from fastapi import FastAPI

from oep_ai_tutor.conversations import router as conversations_router
from oep_ai_tutor.errors import AiTutorError, handle_ai_tutor_error
from oep_ai_tutor.security import AssertionVerifier
from oep_ai_tutor.status import router as status_router


def create_app() -> FastAPI:
    secret = os.environ.get("AI_TUTOR_SERVICE_JWT_SECRET", "")
    app = FastAPI(title="OEP AI Tutor", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.assertion_verifier = AssertionVerifier(secret)
    app.state.database_url = os.environ.get("AI_TUTOR_DATABASE_URL", "")
    app.state.embedding_space_key = os.environ.get("AI_TUTOR_EMBEDDING_SPACE_KEY", "")
    app.add_exception_handler(AiTutorError, handle_ai_tutor_error)
    app.include_router(conversations_router)
    app.include_router(status_router)

    @app.get("/healthz", include_in_schema=False)
    def healthz() -> dict[str, str]:
        return {"status": "ok"}

    return app
