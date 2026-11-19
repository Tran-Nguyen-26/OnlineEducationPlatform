"""FastAPI entry point. Functional routes are added in later checklist items."""

from __future__ import annotations

import os

from fastapi import FastAPI

from oep_ai_tutor.security import AssertionVerifier


def create_app() -> FastAPI:
    secret = os.environ.get("AI_TUTOR_SERVICE_JWT_SECRET", "")
    app = FastAPI(title="OEP AI Tutor", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.assertion_verifier = AssertionVerifier(secret)

    @app.get("/healthz", include_in_schema=False)
    def healthz() -> dict[str, str]:
        return {"status": "ok"}

    return app
