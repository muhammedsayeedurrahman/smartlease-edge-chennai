"""SQLite schema and connection setup.

Only a digest and small metadata are ever stored -- see the column list
below. There is deliberately no column, table, or blob storage anywhere in
this schema that could hold image/video/audio bytes.
"""

from __future__ import annotations

import sqlite3

SCHEMA = """
CREATE TABLE IF NOT EXISTS reports (
    report_id TEXT PRIMARY KEY,
    property_ref TEXT NOT NULL,
    session_type TEXT NOT NULL,
    created_at_epoch_ms INTEGER NOT NULL,
    digest_sha256 TEXT NOT NULL,
    app_version TEXT NOT NULL,
    findings_count INTEGER NOT NULL,
    deposit_rupees INTEGER NOT NULL,
    total_deduction_rupees INTEGER NOT NULL,
    server_received_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS countersignatures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id TEXT NOT NULL REFERENCES reports(report_id),
    signer_role TEXT NOT NULL,
    signature_sha256 TEXT NOT NULL,
    signed_at_epoch_ms INTEGER NOT NULL,
    UNIQUE(report_id, signer_role)
);
"""


def create_connection(database_path: str) -> sqlite3.Connection:
    """Open (and initialise, if needed) the SQLite database at `database_path`.

    `check_same_thread=False` because FastAPI's sync route handlers may run
    on different worker threads; `ReportRepository` guards every access with
    a lock so the connection is never used concurrently from two threads.
    """
    connection = sqlite3.connect(database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.executescript(SCHEMA)
    connection.commit()
    return connection
