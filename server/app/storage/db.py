"""SQLite schema and connection setup.

Only a digest and small metadata are ever stored -- see the column list
below. There is deliberately no column, table, or blob storage anywhere in
this schema that could hold image/video/audio bytes.

`access_token_sha256` holds the SHA-256 of the per-report token, never the
token itself, so a stolen copy of this file does not hand the thief the
ability to read or countersign the reports it describes.
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
    server_received_at_epoch_ms INTEGER NOT NULL,
    access_token_sha256 TEXT
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

# `CREATE TABLE IF NOT EXISTS` is a no-op against a database created before a
# column existed, so new columns need an explicit, idempotent migration.
# Each entry is (table, column, DDL fragment).
_ADDED_COLUMNS = (
    ("reports", "access_token_sha256", "ALTER TABLE reports ADD COLUMN access_token_sha256 TEXT"),
)


def _apply_column_migrations(connection: sqlite3.Connection) -> None:
    for table, column, ddl in _ADDED_COLUMNS:
        existing = {
            row["name"] for row in connection.execute(f"PRAGMA table_info({table})").fetchall()
        }
        if column not in existing:
            connection.execute(ddl)


def create_connection(database_path: str) -> sqlite3.Connection:
    """Open (and initialise or migrate, if needed) the SQLite database at `database_path`.

    `check_same_thread=False` because FastAPI's sync route handlers may run
    on different worker threads; `ReportRepository` guards every access with
    a lock so the connection is never used concurrently from two threads.
    """
    connection = sqlite3.connect(database_path, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.executescript(SCHEMA)
    _apply_column_migrations(connection)
    connection.commit()
    return connection
