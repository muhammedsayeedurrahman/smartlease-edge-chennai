"""Report custody endpoints: create, fetch, countersign, verify.

Route handlers stay thin: they translate between the wire schema (pydantic
models), the storage layer (immutable dataclasses), and the small set of
`AppError` subclasses that the global exception handlers turn into the
standard error envelope. Business rules that don't belong to either layer
(duplicate-digest tamper detection, double-countersign) live here.
"""

from __future__ import annotations

import sqlite3

from fastapi import APIRouter, Query, Request, status
from fastapi.responses import JSONResponse

from app.errors import AlreadyCountersignedError, DigestMismatchError, ReportNotFoundError
from app.models.countersign import CountersignCreateResponse, CountersignRequest
from app.models.report import (
    DIGEST_HEX_PATTERN,
    CountersignatureResponse,
    ReportCreateRequest,
    ReportCreateResponse,
    ReportRecordResponse,
    VerifyResponse,
)
from app.storage.report_repository import (
    CountersignatureRecord,
    ReportRecord,
    ReportRepository,
)
from app.time_utils import now_epoch_ms

router = APIRouter()


def _repository(request: Request) -> ReportRepository:
    return request.app.state.report_repository


def _create_response(record: ReportRecord) -> ReportCreateResponse:
    return ReportCreateResponse(
        reportId=record.report_id,
        digestSha256=record.digest_sha256,
        serverReceivedAtEpochMs=record.server_received_at_epoch_ms,
    )


def _record_response(
    record: ReportRecord, countersignatures: list[CountersignatureRecord]
) -> ReportRecordResponse:
    return ReportRecordResponse(
        reportId=record.report_id,
        propertyRef=record.property_ref,
        sessionType=record.session_type,
        createdAtEpochMs=record.created_at_epoch_ms,
        digestSha256=record.digest_sha256,
        appVersion=record.app_version,
        findingsCount=record.findings_count,
        depositRupees=record.deposit_rupees,
        totalDeductionRupees=record.total_deduction_rupees,
        serverReceivedAtEpochMs=record.server_received_at_epoch_ms,
        countersignatures=[
            CountersignatureResponse(
                signerRole=cs.signer_role,
                signatureSha256=cs.signature_sha256,
                signedAtEpochMs=cs.signed_at_epoch_ms,
            )
            for cs in countersignatures
        ],
    )


@router.post("/reports", status_code=status.HTTP_201_CREATED, response_model=ReportCreateResponse)
def create_report(body: ReportCreateRequest, request: Request):
    repo = _repository(request)
    existing = repo.get_report(body.reportId)

    if existing is not None:
        if existing.digest_sha256 != body.digestSha256:
            raise DigestMismatchError(
                f"Report {body.reportId} already exists with a different "
                "digest; this is a tamper signal, not an ordinary duplicate."
            )
        # Identical resubmission: idempotent 200 with the record as it was
        # first stored.
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content=_create_response(existing).model_dump(),
        )

    record = ReportRecord(
        report_id=body.reportId,
        property_ref=body.propertyRef,
        session_type=body.sessionType,
        created_at_epoch_ms=body.createdAtEpochMs,
        digest_sha256=body.digestSha256,
        app_version=body.appVersion,
        findings_count=body.findingsCount,
        deposit_rupees=body.depositRupees,
        total_deduction_rupees=body.totalDeductionRupees,
        server_received_at_epoch_ms=now_epoch_ms(),
    )
    stored = repo.insert_report(record)
    return _create_response(stored)


@router.get("/reports/{report_id}", response_model=ReportRecordResponse)
def get_report(report_id: str, request: Request):
    repo = _repository(request)
    record = repo.get_report(report_id)
    if record is None:
        raise ReportNotFoundError(f"No report found with id {report_id}")
    countersignatures = repo.list_countersignatures(report_id)
    return _record_response(record, countersignatures)


@router.post(
    "/reports/{report_id}/countersign",
    status_code=status.HTTP_201_CREATED,
    response_model=CountersignCreateResponse,
)
def countersign_report(report_id: str, body: CountersignRequest, request: Request):
    repo = _repository(request)
    report = repo.get_report(report_id)
    if report is None:
        raise ReportNotFoundError(f"No report found with id {report_id}")

    record = CountersignatureRecord(
        report_id=report_id,
        signer_role=body.signerRole,
        signature_sha256=body.signatureSha256,
        signed_at_epoch_ms=body.signedAtEpochMs,
    )
    try:
        stored = repo.insert_countersignature(record)
    except sqlite3.IntegrityError as exc:
        raise AlreadyCountersignedError(
            f"Role {body.signerRole} has already countersigned report {report_id}"
        ) from exc

    return CountersignCreateResponse(
        reportId=stored.report_id,
        signerRole=stored.signer_role,
        signatureSha256=stored.signature_sha256,
        signedAtEpochMs=stored.signed_at_epoch_ms,
    )


@router.get("/reports/{report_id}/verify", response_model=VerifyResponse)
def verify_report(
    report_id: str,
    request: Request,
    digest: str = Query(..., pattern=DIGEST_HEX_PATTERN),
):
    repo = _repository(request)
    record = repo.get_report(report_id)
    if record is None:
        raise ReportNotFoundError(f"No report found with id {report_id}")
    return VerifyResponse(reportId=report_id, matches=(record.digest_sha256 == digest))
