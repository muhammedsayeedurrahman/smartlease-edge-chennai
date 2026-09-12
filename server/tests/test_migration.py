"""Schema migration for the per-report token column.

`CREATE TABLE IF NOT EXISTS` is a no-op against a database that already
exists, so adding `access_token_sha256` to the schema string would silently do
nothing to any database created before the security fix. That failure is
invisible until a query touches the column, which is exactly the kind of thing
that only shows up during a live demo -- hence these tests.
"""

from __future__ import annotations

import sqlite3

from fastapi.testclient import TestClient

from tests.conftest import API, TEST_API_KEY, build_settings, digest_for
from app.main import create_app
from app.storage.db import create_connection

# The `reports` table exactly as it stood before per-report tokens existed.
LEGACY_SCHEMA = """
CREATE TABLE reports (
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
"""

LEGACY_REPORT_ID = "11111111-1111-1111-1111-111111111111"


def _write_legacy_database(path: str) -> None:
    connection = sqlite3.connect(path)
    connection.executescript(LEGACY_SCHEMA)
    connection.execute(
        "INSERT INTO reports VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            LEGACY_REPORT_ID,
            "flat-legacy",
            "MOVE_IN",
            1_700_000_000_000,
            digest_for("legacy-findings"),
            "0.9.0",
            2,
            50_000,
            0,
            1_700_000_005_000,
        ),
    )
    connection.commit()
    connection.close()


def test_column_is_added_to_an_existing_database(tmp_path):
    path = str(tmp_path / "legacy.db")
    _write_legacy_database(path)

    connection = create_connection(path)
    columns = {row["name"] for row in connection.execute("PRAGMA table_info(reports)")}

    assert "access_token_sha256" in columns


def test_migration_is_idempotent(tmp_path):
    path = str(tmp_path / "legacy.db")
    _write_legacy_database(path)

    create_connection(path).close()
    # Opening a second time must not attempt the ALTER again.
    connection = create_connection(path)
    columns = [row["name"] for row in connection.execute("PRAGMA table_info(reports)")]

    assert columns.count("access_token_sha256") == 1


def test_migration_preserves_existing_rows(tmp_path):
    path = str(tmp_path / "legacy.db")
    _write_legacy_database(path)

    connection = create_connection(path)
    row = connection.execute(
        "SELECT * FROM reports WHERE report_id = ?", (LEGACY_REPORT_ID,)
    ).fetchone()

    assert row["property_ref"] == "flat-legacy"
    assert row["access_token_sha256"] is None


def test_legacy_row_without_a_token_fails_closed(tmp_path):
    """A pre-token row must not become world-readable just because it has no token.

    "No token stored" is not "no token required" -- treating it that way would
    have left every report written before the fix open to anyone holding the
    API key.
    """
    path = str(tmp_path / "legacy.db")
    _write_legacy_database(path)
    app = create_app(database_path=path, settings=build_settings())

    with TestClient(app, headers={"X-API-Key": TEST_API_KEY}) as client:
        response = client.get(
            f"{API}/reports/{LEGACY_REPORT_ID}",
            headers={"X-Report-Token": "anything-at-all"},
        )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "forbidden"
