"""GET /health"""

from __future__ import annotations

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
def get_health(request: Request) -> dict:
    settings = request.app.state.settings
    return {"status": "ok", "version": settings.server_version}
