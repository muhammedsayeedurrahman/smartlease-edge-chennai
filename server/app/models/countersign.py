"""Request/response schemas for the countersignature endpoint."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.models.report import DIGEST_HEX_PATTERN

SignerRole = Literal["TENANT", "LANDLORD"]


class CountersignRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    signerRole: SignerRole
    signatureSha256: str = Field(pattern=DIGEST_HEX_PATTERN)
    signedAtEpochMs: int = Field(ge=0)


class CountersignCreateResponse(BaseModel):
    reportId: str
    signerRole: SignerRole
    signatureSha256: str
    signedAtEpochMs: int
