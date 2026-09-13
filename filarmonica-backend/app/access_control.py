import hashlib
import html
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

DATA_DIR = os.getenv("DATA_DIR", "/data")
DB_PATH = os.getenv("ACCESS_DB_PATH", os.path.join(DATA_DIR, "access.db"))
ADMIN_HOST = os.getenv("ADMIN_HOST", "stagiune-admin.accesorii-muzicale.ro").lower()
ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "").strip().lower()
ADMIN_ALLOW_LOCAL = os.getenv("ADMIN_ALLOW_LOCAL", "1") == "1"


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def token_hash(token: str):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _connect():
    Path(DATA_DIR).mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=15)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def init_db():
    with _connect() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                token_hash TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                manufacturer TEXT DEFAULT '',
                model TEXT DEFAULT '',
                device_name TEXT DEFAULT '',
                android_version TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                status TEXT NOT NULL DEFAULT 'pending',
                requested_at TEXT NOT NULL,
                approved_at TEXT,
                denied_at TEXT,
                revoked_at TEXT,
                last_seen_at TEXT,
                last_ip TEXT DEFAULT '',
                last_user_agent TEXT DEFAULT '',
                open_count INTEGER NOT NULL DEFAULT 0,
                notifications_enabled INTEGER NOT NULL DEFAULT 1
            );
            CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
            CREATE INDEX IF NOT EXISTS idx_devices_email ON devices(email);

            CREATE TABLE IF NOT EXISTS activity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_row_id INTEGER NOT NULL,
                action TEXT NOT NULL,
                detail TEXT DEFAULT '',
                created_at TEXT NOT NULL,
                ip TEXT DEFAULT '',
                FOREIGN KEY(device_row_id) REFERENCES devices(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_activity_device_time
                ON activity(device_row_id, created_at DESC);
            """
        )


def request_access(*, device_id: str, device_token: str, name: str, email: str,
                   manufacturer: str = "", model: str = "", device_name: str = "",
                   android_version: str = "", app_version: str = "", ip: str = "",
                   user_agent: str = ""):
    clean_name = (name or "").strip()[:120]
    clean_email = (email or "").strip().lower()[:180]
    clean_device_id = (device_id or "").strip()[:180]
    if len(clean_name) < 2:
        raise ValueError("Numele este obligatoriu")
    if "@" not in clean_email or "." not in clean_email.split("@")[-1]:
        raise ValueError("Email invalid")
    if len(clean_device_id) < 8:
        raise ValueError("ID dispozitiv invalid")
    if len(device_token or "") < 24:
        raise ValueError("Token dispozitiv invalid")

    th = token_hash(device_token)
    now = now_iso()
    with _connect() as db:
        row = db.execute("SELECT * FROM devices WHERE token_hash=?", (th,)).fetchone()
        if row:
            db.execute(
                """UPDATE devices SET name=?, email=?, device_id=?, manufacturer=?, model=?,
                   device_name=?, android_version=?, app_version=?, last_ip=?, last_user_agent=?
                   WHERE id=?""",
                (clean_name, clean_email, clean_device_id, manufacturer[:80], model[:120],
                 device_name[:120], android_version[:80], app_version[:80], ip[:80],
                 user_agent[:500], row["id"]),
            )
            row = db.execute("SELECT * FROM devices WHERE id=?", (row["id"],)).fetchone()
            return dict(row)

        db.execute(
            """INSERT INTO devices
               (device_id, token_hash, name, email, manufacturer, model, device_name,
                android_version, app_version, status, requested_at, last_ip, last_user_agent)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?)""",
            (clean_device_id, th, clean_name, clean_email, manufacturer[:80], model[:120],
             device_name[:120], android_version[:80], app_version[:80], now, ip[:80],
             user_agent[:500]),
        )
        rid = db.execute("SELECT last_insert_rowid()").fetchone()[0]
        row = db.execute("SELECT * FROM devices WHERE id=?", (rid,)).fetchone()
        return dict(row)


def device_by_token(token: str, *, touch: bool = True, ip: str = "", user_agent: str = ""):
    if not token:
        return None
    th = token_hash(token)
    with _connect() as db:
        row = db.execute("SELECT * FROM devices WHERE token_hash=?", (th,)).fetchone()
        if not row:
            return None
        if touch:
            db.execute(
                "UPDATE devices SET last_seen_at=?, last_ip=?, last_user_agent=? WHERE id=?",
                (now_iso(), ip[:80], user_agent[:500], row["id"]),
            )
            row = db.execute("SELECT * FROM devices WHERE id=?", (row["id"],)).fetchone()
        return dict(row)


def public_device(row):
    if not row:
        return None
    return {
        "id": row["id"],
        "device_id": row["device_id"],
        "name": row["name"],
        "email": row["email"],
        "manufacturer": row["manufacturer"],
        "model": row["model"],
        "device_name": row["device_name"],
        "android_version": row["android_version"],
        "app_version": row["app_version"],
        "status": row["status"],
        "requested_at": row["requested_at"],
        "approved_at": row["approved_at"],
        "last_seen_at": row["last_seen_at"],
        "open_count": row["open_count"],
        "notifications_enabled": bool(row["notifications_enabled"]),
    }


def log_activity(device_row_id: int, action: str, detail: str = "", ip: str = ""):
    with _connect() as db:
        db.execute(
            "INSERT INTO activity(device_row_id, action, detail, created_at, ip) VALUES (?, ?, ?, ?, ?)",
            (device_row_id, action[:80], (detail or "")[:1000], now_iso(), ip[:80]),
        )
        if action == "app_open":
            db.execute("UPDATE devices SET open_count=open_count+1 WHERE id=?", (device_row_id,))


def set_notifications(device_row_id: int, enabled: bool):
    with _connect() as db:
        db.execute(
            "UPDATE devices SET notifications_enabled=? WHERE id=?",
            (1 if enabled else 0, device_row_id),
        )
        return bool(enabled)


def admin_set_status(device_row_id: int, action: str):
    now = now_iso()
    with _connect() as db:
        row = db.execute("SELECT * FROM devices WHERE id=?", (device_row_id,)).fetchone()
        if not row:
            return False
        if action == "approve":
            db.execute(
                "UPDATE devices SET status='approved', approved_at=?, denied_at=NULL, revoked_at=NULL WHERE id=?",
                (now, device_row_id),
            )
        elif action == "deny":
            db.execute(
                "UPDATE devices SET status='denied', denied_at=? WHERE id=?",
                (now, device_row_id),
            )
        elif action == "revoke":
            db.execute(
                "UPDATE devices SET status='revoked', revoked_at=? WHERE id=?",
                (now, device_row_id),
            )
        elif action == "pending":
            db.execute(
                "UPDATE devices SET status='pending', denied_at=NULL, revoked_at=NULL WHERE id=?",
                (device_row_id,),
            )
        else:
            return False
        return True


def list_devices():
    with _connect() as db:
        rows = db.execute(
            """SELECT d.*,
               (SELECT detail FROM activity a WHERE a.device_row_id=d.id AND a.action='search'
                ORDER BY a.id DESC LIMIT 1) AS last_search,
               (SELECT created_at FROM activity a WHERE a.device_row_id=d.id
                ORDER BY a.id DESC LIMIT 1) AS last_activity_at
               FROM devices d
               ORDER BY CASE d.status WHEN 'pending' THEN 0 WHEN 'approved' THEN 1 ELSE 2 END,
                        d.requested_at DESC"""
        ).fetchall()
        return [dict(r) for r in rows]


def get_device(device_row_id: int):
    with _connect() as db:
        row = db.execute("SELECT * FROM devices WHERE id=?", (device_row_id,)).fetchone()
        return dict(row) if row else None


def device_activity(device_row_id: int, limit: int = 300):
    with _connect() as db:
        rows = db.execute(
            "SELECT * FROM activity WHERE device_row_id=? ORDER BY id DESC LIMIT ?",
            (device_row_id, limit),
        ).fetchall()
        return [dict(r) for r in rows]


def client_ip(request):
    return (
        request.headers.get("cf-connecting-ip")
        or request.headers.get("x-forwarded-for", "").split(",")[0].strip()
        or (request.client.host if request.client else "")
    )


def is_local_ip(ip: str):
    return (
        ip.startswith("192.168.")
        or ip.startswith("10.")
        or ip.startswith("127.")
        or ip.startswith("172.16.")
        or ip.startswith("172.17.")
        or ip.startswith("172.18.")
        or ip.startswith("172.19.")
        or ip.startswith("172.2")
        or ip.startswith("172.30.")
        or ip.startswith("172.31.")
    )


def admin_authorized(request):
    ip = client_ip(request)
    if ADMIN_ALLOW_LOCAL and is_local_ip(ip):
        return True
    host = request.headers.get("host", "").split(":")[0].lower()
    if ADMIN_HOST and host != ADMIN_HOST:
        return False
    cf_email = request.headers.get("cf-access-authenticated-user-email", "").strip().lower()
    if not cf_email:
        return False
    if ADMIN_EMAIL and cf_email != ADMIN_EMAIL:
        return False
    return True


def esc(value):
    return html.escape(str(value or ""))


init_db()
