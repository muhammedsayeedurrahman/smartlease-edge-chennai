"""Request/response schemas for the report-custody endpoints.

All request models set `extra="forbid"`: any field this contract does not
name is rejected rather than silently ignored. That is part of the privacy
enforcement -- a client cannot smuggle an inspection photo in under an
unexpected field name and have it quietly accepted.
"""

from __future__ import annotations

import uuid
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

DIGEST_HEX_PATTERN = r"^[0-9a-f]{64}$"

SessionType = Literal["MOVE_IN", "MOVE_OUT"]


class ReportCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reportId: str
    propertyRef: str = Field(min_length=1)
    sessionType: SessionType
    createdAtEpochMs: int = Field(ge=0)
    digestSha256: str = Field(pattern=DIGEST_HEX_PATTERN)
    appVersion: str = Field(min_length=1)
    findingsCount: int = Field(ge=0)
    depositRupees: int = Field(ge=0)
    totalDeductionRupees: int = Field(ge=0)

    @field_validator("reportId")
    @classmethod
    def reportId_must_be_uuid(cls, value: str) -> str:
        try:
            uuid.UUID(value)
        except ValueError as exc:
            raise ValueError("reportId must be a valid UUID") from exc
        return value

    @model_validator(mode="after")
    def deduction_must_not_exceed_deposit(self) -> "ReportCreateRequest":
        if self.totalDeductionRupees > self.depositRupees:
            raise ValueError(
                "totalDeductionRupees must not exceed depositRupees "
                f"(got {self.totalDeductionRupees} > {self.depositRupees})"
            )
        return self


class ReportCreateResponse(BaseModel):
    """The response to POST /reports.

    `reportToken` is the per-report secret, and it is returned exactly once --
    in the response that first creates the report. A client that loses it
    cannot fetch or countersign that report again, and the server cannot
    reissue it because only the hash is stored. That is the intended
    trade-off: recoverable tokens would mean a server that can hand any
    caller access to any report.
    """

    reportId: str
    digestSha256: str
    serverReceivedAtEpochMs: int
    # Absent on an idempotent re-submission of an already-stored report: the
    # original token still stands and is not recoverable from the stored hash.
    reportToken: str | None = None


class CountersignatureResponse(BaseModel):
    signerRole: Literal["TENANT", "LANDLORD"]
    signatureSha256: str
    signedAtEpochMs: int


class ReportRecordResponse(BaseModel):
    reportId: str
    propertyRef: str
    sessionType: SessionType
    createdAtEpochMs: int
    digestSha256: str
    appVersion: str
    findingsCount: int
    depositRupees: int
    totalDeductionRupees: int
    serverReceivedAtEpochMs: int
    countersignatures: list[CountersignatureResponse] = Field(default_factory=list)


class VerifyResponse(BaseModel):
    reportId: str
    matches: bool
