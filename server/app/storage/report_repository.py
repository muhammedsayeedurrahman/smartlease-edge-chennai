"""Persistence for reports and their countersignatures.

Records are immutable dataclasses: nothing here ever mutates a `ReportRecord`
or `CountersignatureRecord` in place, and every read builds a fresh instance
from the row it fetched.
"""

from __future__ import annotations

import sqlite3
import threading
from dataclasses import dataclass


@dataclass(frozen=True)
class ReportRecord:
    report_id: str
    property_ref: str
    session_type: str
    created_at_epoch_ms: int
    digest_sha256: str
    app_version: str
    findings_count: int
    deposit_rupees: int
    total_deduction_rupees: int
    server_received_at_epoch_ms: int


@dataclass(frozen=True)
class CountersignatureRecord:
    report_id: str
    signer_role: str
    signature_sha256: str
    signed_at_epoch_ms: int


def _report_from_row(row: sqlite3.Row) -> ReportRecord:
    return ReportRecord(
        report_id=row["report_id"],
        property_ref=row["property_ref"],
        session_type=row["session_type"],
        created_at_epoch_ms=row["created_at_epoch_ms"],
        digest_sha256=row["digest_sha256"],
        app_version=row["app_version"],
        findings_count=row["findings_count"],
        deposit_rupees=row["deposit_rupees"],
        total_deduction_rupees=row["total_deduction_rupees"],
        server_received_at_epoch_ms=row["server_received_at_epoch_ms"],
    )


def _countersignature_from_row(row: sqlite3.Row) -> CountersignatureRecord:
    return CountersignatureRecord(
        report_id=row["report_id"],
        signer_role=row["signer_role"],
        signature_sha256=row["signature_sha256"],
        signed_at_epoch_ms=row["signed_at_epoch_ms"],
    )


class ReportRepository:
    """Thread-safe access to the `reports` and `countersignatures` tables."""

    def __init__(self, connection: sqlite3.Connection) -> None:
        self._connection = connection
        self._lock = threading.Lock()

    def insert_report(self, record: ReportRecord) -> ReportRecord:
        with self._lock:
            self._connection.execute(
                """
                INSERT INTO reports (
                    report_id, property_ref, session_type, created_at_epoch_ms,
                    digest_sha256, app_version, findings_count, deposit_rupees,
                    total_deduction_rupees, server_received_at_epoch_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    record.report_id,
                    record.property_ref,
                    record.session_type,
                    record.created_at_epoch_ms,
                    record.digest_sha256,
                    record.app_version,
                    record.findings_count,
                    record.deposit_rupees,
                    record.total_deduction_rupees,
                    record.server_received_at_epoch_ms,
                ),
            )
            self._connection.commit()
        return record

    def get_report(self, report_id: str) -> ReportRecord | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT * FROM reports WHERE report_id = ?", (report_id,)
            ).fetchone()
        return None if row is None else _report_from_row(row)

    def insert_countersignature(
        self, record: CountersignatureRecord
    ) -> CountersignatureRecord:
        """Raises `sqlite3.IntegrityError` if this role already signed this report."""
        with self._lock:
            try:
                self._connection.execute(
                    """
                    INSERT INTO countersignatures (
                        report_id, signer_role, signature_sha256, signed_at_epoch_ms
                    ) VALUES (?, ?, ?, ?)
                    """,
                    (
                        record.report_id,
                        record.signer_role,
                        record.signature_sha256,
                        record.signed_at_epoch_ms,
                    ),
                )
                self._connection.commit()
            except sqlite3.IntegrityError:
                self._connection.rollback()
                raise
        return record

    def list_countersignatures(self, report_id: str) -> list[CountersignatureRecord]:
        with self._lock:
            rows = self._connection.execute(
                "SELECT * FROM countersignatures WHERE report_id = ? ORDER BY id",
                (report_id,),
            ).fetchall()
        return [_countersignature_from_row(row) for row in rows]
