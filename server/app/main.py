"""FastAPI application factory for the SmartLease Edge report-custody API.

`create_app()` builds a fully independent app instance (its own SQLite
connection, its own repository) so tests can spin up isolated instances
without any shared state. `app` at module scope is the instance uvicorn
serves in production.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.body_guard import JsonOnlyBodyGuardMiddleware
from app.config import API_V1_PREFIX, Settings, get_settings
from app.errors import AppError
from app.routes import health, reports
from app.storage.db import create_connection
from app.storage.report_repository import ReportRepository

logger = logging.getLogger("smartlease_edge.server")


def create_app(database_path: str | None = None) -> FastAPI:
    settings: Settings = get_settings()
    effective_database_path = database_path if database_path is not None else settings.database_path

    app = FastAPI(
        title="SmartLease Edge Report Custody API",
        version=settings.server_version,
    )

    connection = create_connection(effective_database_path)
    app.state.settings = settings
    app.state.report_repository = ReportRepository(connection)

    app.add_middleware(
        JsonOnlyBodyGuardMiddleware,
        max_body_bytes=settings.max_request_body_bytes,
    )

    _register_exception_handlers(app)

    app.include_router(health.router, prefix=API_V1_PREFIX)
    app.include_router(reports.router, prefix=API_V1_PREFIX)

    return app


def _register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.to_envelope())

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        _: Request, exc: RequestValidationError
    ) -> JSONResponse:
        message = "; ".join(
            f"{'.'.join(str(part) for part in error['loc'])}: {error['msg']}"
            for error in exc.errors()
        )
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content={"error": {"code": "validation_error", "message": message}},
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_error(_: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled exception while processing request", exc_info=exc)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "error": {
                    "code": "internal_error",
                    "message": "An unexpected error occurred.",
                }
            },
        )


app = create_app()
