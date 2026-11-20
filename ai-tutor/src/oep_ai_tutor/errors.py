"""Private M7 error envelope consumed by the core LMS proxy."""

from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse


class AiTutorError(Exception):
    def __init__(self, code: str, status: int, message: str):
        super().__init__(message)
        self.code = code
        self.status = status
        self.message = message


def handle_ai_tutor_error(request: Request, exc: AiTutorError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status,
        content={
            "error": {
                "code": exc.code,
                "message": exc.message,
                "request_id": request.headers.get("x-request-id", ""),
                "details": {},
            }
        },
    )
