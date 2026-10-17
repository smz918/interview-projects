import html
import hashlib
import json
import os
import re
import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import List, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field


APP_ROOT = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("CENTRAL_AUTH_DB", APP_ROOT / "auth.db"))
SESSION_DAYS = int(os.getenv("CENTRAL_AUTH_SESSION_DAYS", "7"))
CHINA_TZ = timezone(timedelta(hours=8))

app = FastAPI(title="518 Central Auth", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class LoginRequest(BaseModel):
    username: str
    password: str
    device_id: str = Field(default="")
    device_name: str = Field(default="")
    app_version: str = Field(default="")


class RegisterRequest(BaseModel):
    username: str
    password: str
    referrer: str = ""
    device_id: str = Field(default="")
    device_name: str = Field(default="")
    app_version: str = Field(default="")


class VerifyRequest(BaseModel):
    device_id: str = Field(default="")
    device_name: str = Field(default="")
    app_version: str = Field(default="")


class UserCreate(BaseModel):
    username: str
    password: str
    is_admin: bool = False
    is_active: bool = True
    expires_at: str = ""
    device_limit: int = Field(default=0, ge=0, le=100)
    note: str = ""
    referrer: str = ""
    permissions: List[str] = Field(default_factory=list)
    daily_phone_limit: int = Field(default=100, ge=-1, le=1000000)


class UserStatus(BaseModel):
    user_id: int
    is_active: bool


class UserNote(BaseModel):
    user_id: int
    note: str = ""


class UserPhoneQuota(BaseModel):
    user_id: int
    daily_phone_limit: int = Field(ge=-1, le=1000000)


class PhoneQuotaConsume(BaseModel):
    count: int = Field(default=0, ge=0, le=1000000)


class ActivityCreate(BaseModel):
    event_type: str = Field(default="api_call")
    action: str = Field(default="")
    path: str = Field(default="")
    method: str = Field(default="")
    status_code: int = 0
    success: bool = True
    message: str = ""
    duration_ms: int = 0
    metadata: dict = Field(default_factory=dict)
    device_id: str = Field(default="")
    device_name: str = Field(default="")
    app_version: str = Field(default="")
    run_id: str = Field(default="")
    task_id: int = 0
    module: str = Field(default="")


def now_text():
    return datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S.%f")


def china_time_text(value):
    parsed = parse_time(value)
    if not parsed:
        return value
    return (
        parsed.replace(tzinfo=timezone.utc)
        .astimezone(CHINA_TZ)
        .strftime("%Y-%m-%d %H:%M:%S")
    )


def china_day_start_utc_text():
    now_in_china = datetime.now(CHINA_TZ)
    start_in_china = now_in_china.replace(hour=0, minute=0, second=0, microsecond=0)
    return start_in_china.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def china_day_text():
    return datetime.now(CHINA_TZ).strftime("%Y-%m-%d")


def connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def hash_password(password: str, salt: Optional[str] = None):
    salt = salt or secrets.token_hex(16)
    password_hash = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 150000).hex()
    return password_hash, salt


def parse_time(value):
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value, fmt)
        except ValueError:
            continue
    return None


def row_dict(row):
    data = dict(row)
    data["is_active"] = bool(data.get("is_active"))
    data["is_admin"] = bool(data.get("is_admin"))
    data["permissions"] = [item for item in (data.get("permissions") or "").split(",") if item]
    return data


def display_time_dict(row, *fields):
    data = dict(row)
    for field in fields:
        data[field] = china_time_text(data.get(field))
    return data


def display_activity_dict(row):
    data = display_time_dict(row, "event_time")
    try:
        metadata = json.loads(data.get("metadata") or "{}")
        if not isinstance(metadata, dict):
            metadata = {}
    except (TypeError, ValueError, json.JSONDecodeError):
        metadata = {}
    detail = str(metadata.get("detail") or "").strip()
    action = (data.get("action") or "").strip()
    if detail and action and detail not in action:
        data["message"] = action + "；" + detail
    data["metadata_detail"] = sanitize_log_value(metadata)
    return data


def sanitize_log_value(value):
    sensitive = ("password", "passwd", "token", "authorization", "secret", "api_key", "cookie")
    if isinstance(value, dict):
        return {
            str(key): "[已隐藏]" if any(item in str(key).lower() for item in sensitive)
            else sanitize_log_value(item)
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [sanitize_log_value(item) for item in value]
    return value


def ensure_column(conn, table, column, definition):
    columns = [row["name"] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()]
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


def clean_account(value):
    return (value or "").strip()


def validate_register_input(username, password):
    username = clean_account(username)
    if not username:
        raise HTTPException(status_code=400, detail="请填写手机号")
    if not re.fullmatch(r"1[3-9]\d{9}", username):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    if not password or len(password) < 6:
        raise HTTPException(status_code=400, detail="密码至少 6 位")
    return username


def insert_user(conn, username, password, is_admin=False, is_active=True, expires_at="", note="", referrer="", permissions=None, daily_phone_limit=100):
    username = clean_account(username)
    password_hash, salt = hash_password(password)
    conn.execute(
        """
        INSERT INTO users
            (
                username, password_hash, salt, is_active, is_admin, expires_at,
                device_limit, permissions, password_plain, note, referrer, daily_phone_limit
            )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            username,
            password_hash,
            salt,
            1 if is_active else 0,
            1 if is_admin else 0,
            expires_at,
            0,
            ",".join(permissions or []),
            password,
            note,
            clean_account(referrer),
            int(daily_phone_limit),
        ),
    )


def phone_quota_status(conn, user):
    limit = int(user.get("daily_phone_limit", 100) or 0)
    usage_date = china_day_text()
    row = conn.execute(
        "SELECT used_count FROM phone_daily_usage WHERE user_id = ? AND usage_date = ?",
        (user["id"], usage_date),
    ).fetchone()
    used = int(row["used_count"] if row else 0)
    remaining = -1 if limit < 0 else max(0, limit - used)
    return {
        "daily_limit": limit,
        "used_today": used,
        "remaining_today": remaining,
        "usage_date": usage_date,
        "unlimited": limit < 0,
    }


def init_db():
    with connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                is_active INTEGER DEFAULT 1,
                is_admin INTEGER DEFAULT 0,
                expires_at TEXT DEFAULT '',
                device_limit INTEGER DEFAULT 1,
                permissions TEXT DEFAULT '',
                password_plain TEXT DEFAULT '',
                note TEXT DEFAULT '',
                referrer TEXT DEFAULT '',
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        ensure_column(conn, "users", "password_plain", "TEXT DEFAULT ''")
        ensure_column(conn, "users", "note", "TEXT DEFAULT ''")
        ensure_column(conn, "users", "referrer", "TEXT DEFAULT ''")
        ensure_column(conn, "users", "daily_phone_limit", "INTEGER NOT NULL DEFAULT 100")
        conn.execute("UPDATE users SET daily_phone_limit = -1 WHERE is_admin = 1 AND daily_phone_limit = 100")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                device_id TEXT DEFAULT '',
                device_name TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                expires_at TEXT NOT NULL,
                last_seen_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS login_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username TEXT NOT NULL,
                login_time TEXT DEFAULT CURRENT_TIMESTAMP,
                ip_address TEXT,
                device_id TEXT DEFAULT '',
                device_name TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                success INTEGER DEFAULT 1,
                message TEXT DEFAULT ''
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                device_id TEXT NOT NULL,
                device_name TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                first_seen_at TEXT DEFAULT CURRENT_TIMESTAMP,
                last_seen_at TEXT DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, device_id)
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS activity_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username TEXT DEFAULT '',
                event_time TEXT DEFAULT CURRENT_TIMESTAMP,
                event_type TEXT DEFAULT '',
                action TEXT DEFAULT '',
                path TEXT DEFAULT '',
                method TEXT DEFAULT '',
                status_code INTEGER DEFAULT 0,
                success INTEGER DEFAULT 1,
                message TEXT DEFAULT '',
                duration_ms INTEGER DEFAULT 0,
                ip_address TEXT DEFAULT '',
                device_id TEXT DEFAULT '',
                device_name TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                metadata TEXT DEFAULT '',
                run_id TEXT DEFAULT '',
                task_id INTEGER DEFAULT 0,
                module TEXT DEFAULT ''
            )
            """
        )
        ensure_column(conn, "activity_logs", "run_id", "TEXT DEFAULT ''")
        ensure_column(conn, "activity_logs", "task_id", "INTEGER DEFAULT 0")
        ensure_column(conn, "activity_logs", "module", "TEXT DEFAULT ''")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS phone_daily_usage (
                user_id INTEGER NOT NULL,
                usage_date TEXT NOT NULL,
                used_count INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, usage_date)
            )
            """
        )

        admin_count = conn.execute("SELECT COUNT(*) FROM users WHERE is_admin = 1").fetchone()[0]
        if admin_count == 0:
            username = os.getenv("CENTRAL_ADMIN_USERNAME", "admin")
            password = os.getenv("CENTRAL_ADMIN_PASSWORD", "change-me-now")
            password_hash, salt = hash_password(password)
            conn.execute(
                """
                INSERT INTO users
                    (username, password_hash, salt, is_active, is_admin, device_limit, permissions, daily_phone_limit)
                VALUES (?, ?, ?, 1, 1, 0, ?, -1)
                """,
                (username, password_hash, salt, "admin,all"),
            )


def log_login(conn, payload: LoginRequest, request: Request, user_id=None, success=False, message=""):
    client_ip = request.client.host if request.client else ""
    conn.execute(
        """
        INSERT INTO login_logs
            (user_id, username, ip_address, device_id, device_name, app_version, success, message)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            user_id,
            payload.username,
            client_ip,
            payload.device_id,
            payload.device_name,
            payload.app_version,
            1 if success else 0,
            message,
        ),
    )


def get_user_by_token(token):
    with connect() as conn:
        session = conn.execute("SELECT * FROM sessions WHERE token = ?", (token,)).fetchone()
        if not session:
            return None
        if parse_time(session["expires_at"]) and parse_time(session["expires_at"]) < datetime.utcnow():
            conn.execute("DELETE FROM sessions WHERE token = ?", (token,))
            return None
        user = conn.execute("SELECT * FROM users WHERE id = ?", (session["user_id"],)).fetchone()
        if not user or not user["is_active"]:
            return None
        expires_at = parse_time(user["expires_at"])
        if expires_at and expires_at < datetime.utcnow():
            conn.execute("UPDATE users SET is_active = 0 WHERE id = ?", (user["id"],))
            conn.execute("DELETE FROM sessions WHERE user_id = ?", (user["id"],))
            return None
        conn.execute("UPDATE sessions SET last_seen_at = ? WHERE token = ?", (now_text(), token))
        data = row_dict(user)
        data["session"] = row_dict(session)
        return data


def require_user(authorization: str = Header(default="")):
    if not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing login token")
    user = get_user_by_token(authorization.split(" ", 1)[1].strip())
    if not user:
        raise HTTPException(status_code=401, detail="Login expired or account disabled")
    return user


def require_admin(user=Depends(require_user)):
    if not user.get("is_admin"):
        raise HTTPException(status_code=403, detail="Admin permission required")
    return user


def client_ip(request: Request):
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        return forwarded.split(",", 1)[0].strip()
    return request.client.host if request.client else ""


def log_activity(conn, request: Request, user, payload: ActivityCreate):
    session = user.get("session") or {}
    run_id = payload.run_id or str((payload.metadata or {}).get("run_id", ""))
    task_id = payload.task_id or int((payload.metadata or {}).get("task_id", 0) or 0)
    event_time = datetime.utcnow()
    duration_ms = max(0, int(payload.duration_ms or 0))
    if duration_ms <= 0 and (run_id or task_id):
        previous = conn.execute(
            """
            SELECT event_time FROM activity_logs
            WHERE username = ? AND (? = '' OR run_id = ?) AND (? = 0 OR task_id = ?)
            ORDER BY id DESC LIMIT 1
            """,
            (user.get("username", ""), run_id, run_id, task_id, task_id),
        ).fetchone()
        if previous:
            parsed = parse_time(previous["event_time"])
            if parsed:
                duration_ms = max(0, int((event_time - parsed).total_seconds() * 1000))
    conn.execute(
        """
        INSERT INTO activity_logs
            (
                user_id, username, event_time, event_type, action, path, method, status_code,
                success, message, duration_ms, ip_address, device_id, device_name,
                app_version, metadata, run_id, task_id, module
            )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            user.get("id"),
            user.get("username", ""),
            event_time.strftime("%Y-%m-%d %H:%M:%S.%f"),
            payload.event_type,
            payload.action,
            payload.path,
            payload.method,
            payload.status_code,
            1 if payload.success else 0,
            payload.message,
            duration_ms,
            client_ip(request),
            payload.device_id or session.get("device_id", ""),
            payload.device_name or session.get("device_name", ""),
            payload.app_version or session.get("app_version", ""),
            json.dumps(payload.metadata or {}, ensure_ascii=False),
            run_id,
            task_id,
            payload.module or str((payload.metadata or {}).get("module", "")),
        ),
    )


@app.on_event("startup")
def startup():
    init_db()


@app.get("/health")
def health():
    return {"ok": True, "time": now_text()}


@app.get("/", response_class=HTMLResponse)
def admin_page():
    html_path = APP_ROOT / "admin.html"
    if html_path.exists():
        return HTMLResponse(html_path.read_text(encoding="utf-8"))
    return HTMLResponse(ADMIN_HTML)


@app.post("/api/auth/login")
def login(payload: LoginRequest, request: Request):
    username = clean_account(payload.username)
    with connect() as conn:
        user = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
        if not user:
            log_login(conn, payload, request, success=False, message="user_not_found")
            raise HTTPException(status_code=401, detail="账号或密码错误，或账号已被禁用")

        password_hash, _ = hash_password(payload.password, user["salt"])
        if password_hash != user["password_hash"] or not user["is_active"]:
            log_login(conn, payload, request, user_id=user["id"], success=False, message="invalid_or_disabled")
            raise HTTPException(status_code=401, detail="账号或密码错误，或账号已被禁用")

        expires_at = parse_time(user["expires_at"])
        if expires_at and expires_at < datetime.utcnow():
            log_login(conn, payload, request, user_id=user["id"], success=False, message="expired")
            raise HTTPException(status_code=403, detail="账号已到期")

        if payload.device_id:
            known = conn.execute(
                "SELECT COUNT(*) FROM devices WHERE user_id = ? AND device_id != ?",
                (user["id"], payload.device_id),
            ).fetchone()[0]
            limit = int(user["device_limit"] or 0)
            if limit > 0 and known >= limit:
                log_login(conn, payload, request, user_id=user["id"], success=False, message="device_limit")
                raise HTTPException(status_code=403, detail="账号绑定设备数已达上限")
            current_time = now_text()
            updated = conn.execute(
                """
                UPDATE devices
                SET device_name = ?, app_version = ?, last_seen_at = ?
                WHERE user_id = ? AND device_id = ?
                """,
                (payload.device_name, payload.app_version, current_time, user["id"], payload.device_id),
            )
            if updated.rowcount == 0:
                conn.execute(
                    """
                    INSERT OR IGNORE INTO devices
                        (user_id, device_id, device_name, app_version, first_seen_at, last_seen_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        user["id"],
                        payload.device_id,
                        payload.device_name,
                        payload.app_version,
                        current_time,
                        current_time,
                    ),
                )

        token = secrets.token_urlsafe(32)
        session_expires = (datetime.utcnow() + timedelta(days=SESSION_DAYS)).strftime("%Y-%m-%d %H:%M:%S")
        conn.execute(
            """
            INSERT INTO sessions (token, user_id, device_id, device_name, app_version, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (token, user["id"], payload.device_id, payload.device_name, payload.app_version, session_expires),
        )
        log_login(conn, payload, request, user_id=user["id"], success=True, message="ok")
        data = row_dict(user)
        return {
            "token": token,
            "username": data["username"],
            "is_admin": data["is_admin"],
            "expires_at": data.get("expires_at") or "",
            "permissions": data.get("permissions") or [],
        }


@app.post("/api/auth/register")
def register(payload: RegisterRequest, request: Request):
    username = validate_register_input(payload.username, payload.password)
    try:
        with connect() as conn:
            insert_user(
                conn,
                username=username,
                password=payload.password,
                is_admin=False,
                is_active=True,
                expires_at="",
                note="",
                referrer=payload.referrer,
                permissions=[],
            )
    except sqlite3.IntegrityError:
        raise HTTPException(status_code=400, detail="这个手机号已经注册过，请直接登录")
    return login(
        LoginRequest(
            username=username,
            password=payload.password,
            device_id=payload.device_id,
            device_name=payload.device_name,
            app_version=payload.app_version,
        ),
        request,
    )


@app.post("/api/auth/verify")
def verify(payload: VerifyRequest, user=Depends(require_user)):
    return {
        "ok": True,
        "username": user["username"],
        "is_admin": user["is_admin"],
        "expires_at": user.get("expires_at") or "",
        "permissions": user.get("permissions") or [],
    }


@app.get("/api/auth/users")
def list_users(user=Depends(require_admin)):
    usage_date = china_day_text()
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT users.id, users.username, users.is_active, users.is_admin,
                   users.expires_at, users.device_limit, users.permissions,
                   users.password_plain, users.note, users.referrer, users.created_at,
                   users.daily_phone_limit,
                   COALESCE(phone_daily_usage.used_count, 0) AS phone_used_today
            FROM users
            LEFT JOIN phone_daily_usage
              ON phone_daily_usage.user_id = users.id
             AND phone_daily_usage.usage_date = ?
            ORDER BY users.id
            """,
            (usage_date,),
        ).fetchall()
        result = []
        for row in rows:
            data = row_dict(row)
            data["created_at"] = china_time_text(data.get("created_at"))
            limit = int(data.get("daily_phone_limit", 100) or 0)
            used = int(data.get("phone_used_today", 0) or 0)
            data["phone_remaining_today"] = -1 if limit < 0 else max(0, limit - used)
            result.append(data)
        return result


@app.post("/api/auth/users")
def create_user(payload: UserCreate, user=Depends(require_admin)):
    try:
        with connect() as conn:
            insert_user(
                conn,
                username=payload.username,
                password=payload.password,
                is_admin=payload.is_admin,
                is_active=payload.is_active,
                expires_at=payload.expires_at,
                note=payload.note,
                referrer=payload.referrer,
                permissions=payload.permissions,
                daily_phone_limit=-1 if payload.is_admin else payload.daily_phone_limit,
            )
    except sqlite3.IntegrityError:
        raise HTTPException(status_code=400, detail="用户名已存在")
    return {"message": "用户创建成功"}


@app.put("/api/auth/users/status")
def update_user_status(payload: UserStatus, user=Depends(require_admin)):
    with connect() as conn:
        target = conn.execute("SELECT is_admin FROM users WHERE id = ?", (payload.user_id,)).fetchone()
        if not target:
            raise HTTPException(status_code=404, detail="用户不存在")
        if target["is_admin"] and not payload.is_active:
            raise HTTPException(status_code=400, detail="不能禁用管理员账号")
        conn.execute("UPDATE users SET is_active = ? WHERE id = ?", (1 if payload.is_active else 0, payload.user_id))
        if not payload.is_active:
            conn.execute("DELETE FROM sessions WHERE user_id = ?", (payload.user_id,))
    return {"message": "用户状态更新成功"}


@app.put("/api/auth/users/note")
def update_user_note(payload: UserNote, user=Depends(require_admin)):
    with connect() as conn:
        target = conn.execute("SELECT id FROM users WHERE id = ?", (payload.user_id,)).fetchone()
        if not target:
            raise HTTPException(status_code=404, detail="用户不存在")
        conn.execute("UPDATE users SET note = ? WHERE id = ?", (payload.note, payload.user_id))
    return {"message": "备注更新成功"}


@app.put("/api/auth/users/phone-quota")
def update_user_phone_quota(payload: UserPhoneQuota, user=Depends(require_admin)):
    with connect() as conn:
        target = conn.execute("SELECT id, is_admin FROM users WHERE id = ?", (payload.user_id,)).fetchone()
        if not target:
            raise HTTPException(status_code=404, detail="用户不存在")
        limit = -1 if target["is_admin"] else payload.daily_phone_limit
        conn.execute(
            "UPDATE users SET daily_phone_limit = ? WHERE id = ?",
            (limit, payload.user_id),
        )
    return {"message": "电话每日额度更新成功", "daily_phone_limit": limit}


@app.get("/api/auth/phone-quota")
def get_phone_quota(user=Depends(require_user)):
    with connect() as conn:
        return phone_quota_status(conn, user)


@app.post("/api/auth/phone-quota/consume")
def consume_phone_quota(payload: PhoneQuotaConsume, request: Request, user=Depends(require_user)):
    requested = max(0, int(payload.count))
    with connect() as conn:
        conn.execute("BEGIN IMMEDIATE")
        current = phone_quota_status(conn, user)
        limit = current["daily_limit"]
        accepted = requested if limit < 0 else min(requested, current["remaining_today"])
        if accepted > 0:
            updated = conn.execute(
                """
                UPDATE phone_daily_usage
                SET used_count = used_count + ?, updated_at = ?
                WHERE user_id = ? AND usage_date = ?
                """,
                (accepted, now_text(), user["id"], current["usage_date"]),
            )
            if updated.rowcount == 0:
                conn.execute(
                    """
                    INSERT INTO phone_daily_usage
                        (user_id, usage_date, used_count, updated_at)
                    VALUES (?, ?, ?, ?)
                    """,
                    (user["id"], current["usage_date"], accepted, now_text()),
                )
        updated = phone_quota_status(conn, user)
        log_activity(
            conn,
            request,
            user,
            ActivityCreate(
                event_type="api_call",
                action="电话获取额度扣减",
                path="/api/auth/phone-quota/consume",
                method="POST",
                status_code=200,
                success=True,
                message=f"本次返回 {accepted} 条；今日已用 {updated['used_today']} 条",
                module="phone_acquisition",
                metadata={
                    "requested_count": requested,
                    "accepted_count": accepted,
                    **updated,
                },
            ),
        )
        return {"accepted_count": accepted, **updated}


@app.delete("/api/auth/users/{user_id}")
def delete_user(user_id: int, user=Depends(require_admin)):
    with connect() as conn:
        target = conn.execute("SELECT is_admin FROM users WHERE id = ?", (user_id,)).fetchone()
        if not target:
            raise HTTPException(status_code=404, detail="用户不存在")
        if target["is_admin"]:
            raise HTTPException(status_code=400, detail="不能删除管理员账号")
        conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
        conn.execute("DELETE FROM sessions WHERE user_id = ?", (user_id,))
        conn.execute("DELETE FROM devices WHERE user_id = ?", (user_id,))
        conn.execute("DELETE FROM phone_daily_usage WHERE user_id = ?", (user_id,))
    return {"message": "用户删除成功"}


@app.get("/api/auth/login-logs")
def login_logs(user=Depends(require_admin), limit: int = 200):
    safe_limit = max(1, min(limit, 1000))
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM login_logs ORDER BY login_time DESC LIMIT ?",
            (safe_limit,),
        ).fetchall()
        return [display_time_dict(row, "login_time") for row in rows]


@app.post("/api/auth/activity")
def create_activity(payload: ActivityCreate, request: Request, user=Depends(require_user)):
    with connect() as conn:
        log_activity(conn, request, user, payload)
    return {"ok": True}


@app.get("/api/auth/activity")
def activity_logs(user=Depends(require_admin), limit: int = 300, username: str = "",
                  module: str = "", view_mode: str = "key"):
    safe_limit = max(1, min(limit, 2000))
    mode = (view_mode or "key").strip().lower()
    where_parts = []
    if mode == "failed":
        where_parts.append("success = 0")
    elif mode != "all":
        where_parts.append(
            "event_type IN ('business_action', 'task_start', 'task_stop', "
            "'app_action', 'app_session')"
        )
    params = []
    if username:
        where_parts.append("username = ?")
        params.append(username)
    if module:
        where_parts.append("module = ?")
        params.append(module)
    where = "WHERE " + " AND ".join(where_parts) if where_parts else ""
    params.append(safe_limit)
    with connect() as conn:
        rows = conn.execute(
            f"""
            SELECT *
            FROM activity_logs
            {where}
            ORDER BY event_time DESC
            LIMIT ?
            """,
            params,
        ).fetchall()
        return [display_activity_dict(row) for row in rows]


@app.get("/api/auth/activity/{activity_id}/timeline")
def activity_timeline(activity_id: int, user=Depends(require_admin)):
    with connect() as conn:
        anchor = conn.execute(
            "SELECT * FROM activity_logs WHERE id = ?", (activity_id,)
        ).fetchone()
        if not anchor:
            raise HTTPException(status_code=404, detail="行为记录不存在")
        anchor_data = dict(anchor)
        task_id = int(anchor_data.get("task_id") or 0)
        run_id = (anchor_data.get("run_id") or "").strip()
        if not run_id:
            try:
                run_id = str(json.loads(anchor_data.get("metadata") or "{}").get("run_id", ""))
            except (TypeError, ValueError, json.JSONDecodeError):
                run_id = ""
        if run_id:
            rows = conn.execute(
                """
                SELECT * FROM activity_logs
                WHERE username = ? AND run_id = ?
                ORDER BY event_time ASC, id ASC
                LIMIT 2000
                """,
                (anchor_data.get("username", ""), run_id),
            ).fetchall()
            if len(rows) == 1 and task_id > 0 and anchor_data.get("event_type") == "task_start":
                next_start = conn.execute(
                    """
                    SELECT id FROM activity_logs
                    WHERE username = ? AND task_id = ? AND event_type = 'task_start' AND id > ?
                    ORDER BY id LIMIT 1
                    """,
                    (anchor_data.get("username", ""), task_id, activity_id),
                ).fetchone()
                end_id = int(next_start["id"]) if next_start else activity_id + 2000
                rows = conn.execute(
                    """
                    SELECT * FROM activity_logs
                    WHERE username = ? AND task_id = ? AND id >= ? AND id < ?
                    ORDER BY event_time ASC, id ASC
                    LIMIT 2000
                    """,
                    (anchor_data.get("username", ""), task_id, activity_id, end_id),
                ).fetchall()
        elif task_id > 0:
            rows = conn.execute(
                """
                SELECT * FROM activity_logs
                WHERE username = ? AND task_id = ?
                ORDER BY event_time ASC, id ASC
                LIMIT 2000
                """,
                (anchor_data.get("username", ""), task_id),
            ).fetchall()
        else:
            rows = [anchor]
        return {
            "run_id": run_id,
            "task_id": task_id,
            "username": anchor_data.get("username", ""),
            "module": anchor_data.get("module", ""),
            "events": [display_activity_dict(row) for row in rows],
        }


@app.get("/api/auth/devices")
def devices(user=Depends(require_admin), limit: int = 500):
    safe_limit = max(1, min(limit, 2000))
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT
                devices.*,
                users.username
            FROM devices
            LEFT JOIN users ON users.id = devices.user_id
            ORDER BY devices.last_seen_at DESC
            LIMIT ?
            """,
            (safe_limit,),
        ).fetchall()
        return [display_time_dict(row, "first_seen_at", "last_seen_at") for row in rows]


@app.get("/api/auth/summary")
def summary(user=Depends(require_admin)):
    today_start_utc = china_day_start_utc_text()
    with connect() as conn:
        users_total = conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]
        users_active = conn.execute("SELECT COUNT(*) FROM users WHERE is_active = 1").fetchone()[0]
        login_today = conn.execute(
            "SELECT COUNT(*) FROM login_logs WHERE login_time >= ? AND success = 1",
            (today_start_utc,),
        ).fetchone()[0]
        activity_today = conn.execute(
            "SELECT COUNT(*) FROM activity_logs WHERE event_type IN ('business_action', 'task_start', 'task_stop', 'app_action', 'app_session') AND event_time >= ?",
            (today_start_utc,),
        ).fetchone()[0]
        active_sessions = conn.execute(
            "SELECT COUNT(*) FROM sessions WHERE expires_at > ?",
            (now_text(),),
        ).fetchone()[0]
    return {
        "users_total": users_total,
        "users_active": users_active,
        "login_today": login_today,
        "activity_today": activity_today,
        "active_sessions": active_sessions,
    }


ADMIN_HTML = r"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>518 授权管理后台</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f4f7fb;
      --panel: #fff;
      --line: #dce4f0;
      --text: #102033;
      --muted: #66758a;
      --blue: #2563eb;
      --blue-dark: #1d4ed8;
      --green: #13a365;
      --red: #dc2626;
      --amber: #b45309;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    button, input, select {
      font: inherit;
    }
    button {
      border: 1px solid var(--line);
      background: #fff;
      border-radius: 6px;
      padding: 8px 12px;
      cursor: pointer;
    }
    button.primary {
      background: var(--blue);
      border-color: var(--blue);
      color: #fff;
    }
    button.primary:hover { background: var(--blue-dark); }
    button.danger {
      background: #fff5f5;
      border-color: #fecaca;
      color: var(--red);
    }
    input, select {
      width: 100%;
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 9px 10px;
      background: #fff;
    }
    .login {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
    }
    .login-card {
      width: min(420px, 100%);
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      box-shadow: 0 12px 32px rgba(15, 23, 42, .08);
      padding: 28px;
    }
    .login-card h1 { margin: 0 0 8px; font-size: 26px; }
    .login-card p { margin: 0 0 22px; color: var(--muted); }
    .field { margin: 14px 0; }
    .field label { display: block; margin-bottom: 6px; color: #40516a; font-size: 14px; }
    .shell { min-height: 100vh; display: grid; grid-template-columns: 248px 1fr; }
    .sidebar {
      background: #fff;
      border-right: 1px solid var(--line);
      padding: 26px 20px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .brand { display: flex; gap: 12px; align-items: center; }
    .brand-mark {
      width: 48px; height: 48px; border-radius: 8px;
      background: var(--blue); color: #fff;
      display: grid; place-items: center; font-weight: 800; font-size: 20px;
    }
    .brand strong { display: block; font-size: 20px; }
    .brand span { color: var(--muted); font-size: 13px; }
    .nav { display: grid; gap: 8px; }
    .nav button {
      text-align: left;
      border: 0;
      padding: 12px 14px;
      color: #243954;
      background: transparent;
    }
    .nav button.active {
      background: #e8f0ff;
      color: var(--blue);
      font-weight: 700;
    }
    .logout { margin-top: auto; text-align: left; }
    .main { padding: 34px clamp(22px, 5vw, 54px); }
    .topbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 22px;
      gap: 16px;
    }
    .topbar h1 { margin: 0; font-size: 30px; }
    .cards {
      display: grid;
      grid-template-columns: repeat(5, minmax(120px, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }
    .metric {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 16px;
    }
    .metric span { color: var(--muted); font-size: 13px; }
    .metric strong { display: block; margin-top: 8px; font-size: 26px; }
    .panel {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 18px;
      box-shadow: 0 8px 24px rgba(15, 23, 42, .05);
      margin-bottom: 18px;
    }
    .panel-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
    }
    .panel h2 { margin: 0; font-size: 20px; }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th, td {
      border-bottom: 1px solid #edf1f6;
      padding: 11px 12px;
      text-align: left;
      vertical-align: top;
    }
    th { background: #f7f9fc; color: #52647b; font-weight: 700; }
    .badge {
      display: inline-flex;
      align-items: center;
      border-radius: 999px;
      padding: 4px 10px;
      font-size: 12px;
      font-weight: 700;
    }
    .ok { background: #dcfce7; color: #166534; }
    .off { background: #fee2e2; color: #991b1b; }
    .admin { background: #fef3c7; color: #92400e; }
    .muted { color: var(--muted); }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .toolbar {
      display: grid;
      grid-template-columns: 220px 120px auto;
      gap: 10px;
      align-items: end;
    }
    .modal {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, .45);
      display: none;
      place-items: center;
      padding: 22px;
    }
    .modal.open { display: grid; }
    .dialog {
      width: min(520px, 100%);
      background: #fff;
      border-radius: 8px;
      padding: 22px;
      border: 1px solid var(--line);
    }
    .dialog h2 { margin: 0 0 14px; }
    .dialog-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      margin-top: 18px;
    }
    .error { color: var(--red); margin-top: 12px; }
    .hidden { display: none; }
    @media (max-width: 980px) {
      .shell { grid-template-columns: 1fr; }
      .sidebar { position: static; }
      .cards { grid-template-columns: repeat(2, 1fr); }
      .toolbar { grid-template-columns: 1fr; }
      table { display: block; overflow-x: auto; white-space: nowrap; }
    }
  </style>
</head>
<body>
  <section id="loginPage" class="login">
    <form class="login-card" id="loginForm">
      <h1>518 授权管理后台</h1>
      <p>管理客户账号、设备、登录记录和软件使用审计。</p>
      <div class="field">
        <label>管理员账号</label>
        <input id="loginUser" autocomplete="username" value="admin" />
      </div>
      <div class="field">
        <label>密码</label>
        <input id="loginPass" type="password" autocomplete="current-password" />
      </div>
      <button class="primary" style="width:100%">登录</button>
      <div id="loginError" class="error"></div>
    </form>
  </section>

  <section id="appPage" class="shell hidden">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">518</div>
        <div><strong>授权后台</strong><span>中央用户管理</span></div>
      </div>
      <nav class="nav">
        <button data-view="dashboard" class="active">总览</button>
        <button data-view="users">用户管理</button>
        <button data-view="activity">使用审计</button>
        <button data-view="logins">登录日志</button>
        <button data-view="devices">设备记录</button>
      </nav>
      <button class="logout" id="logoutBtn">退出登录</button>
    </aside>
    <main class="main">
      <div class="topbar">
        <h1 id="pageTitle">总览</h1>
        <button class="primary" id="addUserBtn">添加用户</button>
      </div>

      <div id="dashboardView">
        <div class="cards">
          <div class="metric"><span>总用户</span><strong id="mUsers">0</strong></div>
          <div class="metric"><span>启用用户</span><strong id="mActive">0</strong></div>
          <div class="metric"><span>今日登录</span><strong id="mLogin">0</strong></div>
          <div class="metric"><span>今日操作</span><strong id="mActivity">0</strong></div>
          <div class="metric"><span>有效会话</span><strong id="mSessions">0</strong></div>
        </div>
      </div>

      <div id="usersView" class="panel hidden">
        <div class="panel-head"><h2>用户列表</h2><button id="refreshUsers">刷新</button></div>
        <table>
          <thead><tr><th>ID</th><th>用户名</th><th>角色</th><th>状态</th><th>设备上限</th><th>到期时间</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody id="usersBody"></tbody>
        </table>
      </div>

      <div id="activityView" class="panel hidden">
        <div class="panel-head">
          <h2>使用审计</h2>
          <div class="toolbar">
            <input id="activityUser" placeholder="按用户名筛选，可留空" />
            <select id="activityLimit"><option>100</option><option selected>300</option><option>1000</option></select>
            <button id="refreshActivity">刷新</button>
          </div>
        </div>
        <table>
          <thead><tr><th>时间</th><th>用户</th><th>动作</th><th>接口</th><th>状态</th><th>设备</th><th>IP</th><th>耗时</th><th>消息</th></tr></thead>
          <tbody id="activityBody"></tbody>
        </table>
      </div>

      <div id="loginsView" class="panel hidden">
        <div class="panel-head"><h2>登录日志</h2><button id="refreshLogins">刷新</button></div>
        <table>
          <thead><tr><th>时间</th><th>用户</th><th>状态</th><th>IP</th><th>设备</th><th>版本</th><th>消息</th></tr></thead>
          <tbody id="loginsBody"></tbody>
        </table>
      </div>

      <div id="devicesView" class="panel hidden">
        <div class="panel-head"><h2>设备记录</h2><button id="refreshDevices">刷新</button></div>
        <table>
          <thead><tr><th>用户</th><th>设备名</th><th>设备ID</th><th>版本</th><th>首次出现</th><th>最后出现</th></tr></thead>
          <tbody id="devicesBody"></tbody>
        </table>
      </div>
    </main>
  </section>

  <div id="userModal" class="modal">
    <form class="dialog" id="userForm">
      <h2>添加用户</h2>
      <div class="field"><label>用户名</label><input id="newUsername" required /></div>
      <div class="field"><label>密码</label><input id="newPassword" type="password" required /></div>
      <div class="field"><label>角色</label><select id="newRole"><option value="user">普通用户</option><option value="admin">管理员</option></select></div>
      <div class="field"><label>设备上限，0 表示不限</label><input id="newDeviceLimit" type="number" min="0" value="1" /></div>
      <div class="field"><label>到期时间，可留空。格式：2026-12-31</label><input id="newExpiresAt" /></div>
      <div class="dialog-actions">
        <button type="button" id="cancelUser">取消</button>
        <button class="primary">创建</button>
      </div>
      <div id="userError" class="error"></div>
    </form>
  </div>

  <script>
    const state = { token: localStorage.getItem('central_auth_token') || '', view: 'dashboard' };
    const $ = (id) => document.getElementById(id);
    const escapeText = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const yesNo = (value) => value ? '<span class="badge ok">成功</span>' : '<span class="badge off">失败</span>';

    async function api(path, options = {}) {
      const headers = new Headers(options.headers || {});
      headers.set('Accept', 'application/json');
      if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
      if (state.token) headers.set('Authorization', 'Bearer ' + state.token);
      const response = await fetch(path, { ...options, headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.detail || data.message || '请求失败');
      return data;
    }

    function showApp() {
      $('loginPage').classList.add('hidden');
      $('appPage').classList.remove('hidden');
      switchView(state.view);
    }

    function showLogin() {
      $('loginPage').classList.remove('hidden');
      $('appPage').classList.add('hidden');
    }

    async function login(event) {
      event.preventDefault();
      $('loginError').textContent = '';
      try {
        const data = await api('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({
            username: $('loginUser').value.trim(),
            password: $('loginPass').value,
            device_name: 'central-admin-web',
            app_version: 'web'
          })
        });
        if (!data.is_admin) throw new Error('当前账号不是管理员');
        state.token = data.token;
        localStorage.setItem('central_auth_token', state.token);
        showApp();
      } catch (error) {
        $('loginError').textContent = error.message;
      }
    }

    async function verifySession() {
      if (!state.token) return showLogin();
      try {
        const data = await api('/api/auth/verify', { method: 'POST', body: JSON.stringify({ device_name: 'central-admin-web' }) });
        if (!data.is_admin) throw new Error('not admin');
        showApp();
      } catch {
        localStorage.removeItem('central_auth_token');
        state.token = '';
        showLogin();
      }
    }

    function switchView(view) {
      state.view = view;
      const titles = { dashboard: '总览', users: '用户管理', activity: '使用审计', logins: '登录日志', devices: '设备记录' };
      $('pageTitle').textContent = titles[view] || '总览';
      $('addUserBtn').style.display = view === 'users' ? '' : 'none';
      for (const name of Object.keys(titles)) {
        const panel = $(name + 'View');
        if (panel) panel.classList.toggle('hidden', name !== view);
      }
      document.querySelectorAll('.nav button').forEach(btn => btn.classList.toggle('active', btn.dataset.view === view));
      if (view === 'dashboard') loadSummary();
      if (view === 'users') loadUsers();
      if (view === 'activity') loadActivity();
      if (view === 'logins') loadLogins();
      if (view === 'devices') loadDevices();
    }

    async function loadSummary() {
      const data = await api('/api/auth/summary');
      $('mUsers').textContent = data.users_total;
      $('mActive').textContent = data.users_active;
      $('mLogin').textContent = data.login_today;
      $('mActivity').textContent = data.activity_today;
      $('mSessions').textContent = data.active_sessions;
    }

    async function loadUsers() {
      const rows = await api('/api/auth/users');
      $('usersBody').innerHTML = rows.map(user => `
        <tr>
          <td>${user.id}</td>
          <td>${escapeText(user.username)}</td>
          <td><span class="badge ${user.is_admin ? 'admin' : 'ok'}">${user.is_admin ? '管理员' : '普通用户'}</span></td>
          <td><span class="badge ${user.is_active ? 'ok' : 'off'}">${user.is_active ? '启用' : '禁用'}</span></td>
          <td>${user.device_limit || 0}</td>
          <td>${escapeText(user.expires_at || '不限')}</td>
          <td>${escapeText(user.created_at)}</td>
          <td class="actions">
            <button data-toggle="${user.id}" data-active="${user.is_active ? '0' : '1'}">${user.is_active ? '禁用' : '启用'}</button>
            ${user.is_admin ? '' : `<button class="danger" data-delete="${user.id}">删除</button>`}
          </td>
        </tr>
      `).join('');
    }

    async function loadActivity() {
      const limit = encodeURIComponent($('activityLimit').value || '300');
      const username = encodeURIComponent($('activityUser').value.trim());
      const rows = await api(`/api/auth/activity?limit=${limit}&username=${username}`);
      $('activityBody').innerHTML = rows.map(row => `
        <tr>
          <td>${escapeText(row.event_time)}</td>
          <td>${escapeText(row.username)}</td>
          <td>${escapeText(row.action || row.event_type)}</td>
          <td><span class="muted">${escapeText(row.method)} ${escapeText(row.path)}</span></td>
          <td>${row.success ? '<span class="badge ok">' + row.status_code + '</span>' : '<span class="badge off">' + row.status_code + '</span>'}</td>
          <td>${escapeText(row.device_name)}</td>
          <td>${escapeText(row.ip_address)}</td>
          <td>${row.duration_ms || 0}ms</td>
          <td>${escapeText(row.message)}</td>
        </tr>
      `).join('');
    }

    async function loadLogins() {
      const rows = await api('/api/auth/login-logs?limit=300');
      $('loginsBody').innerHTML = rows.map(row => `
        <tr>
          <td>${escapeText(row.login_time)}</td>
          <td>${escapeText(row.username)}</td>
          <td>${yesNo(row.success)}</td>
          <td>${escapeText(row.ip_address)}</td>
          <td>${escapeText(row.device_name)}</td>
          <td>${escapeText(row.app_version)}</td>
          <td>${escapeText(row.message)}</td>
        </tr>
      `).join('');
    }

    async function loadDevices() {
      const rows = await api('/api/auth/devices');
      $('devicesBody').innerHTML = rows.map(row => `
        <tr>
          <td>${escapeText(row.username)}</td>
          <td>${escapeText(row.device_name)}</td>
          <td><span class="muted">${escapeText(row.device_id)}</span></td>
          <td>${escapeText(row.app_version)}</td>
          <td>${escapeText(row.first_seen_at)}</td>
          <td>${escapeText(row.last_seen_at)}</td>
        </tr>
      `).join('');
    }

    async function createUser(event) {
      event.preventDefault();
      $('userError').textContent = '';
      try {
        await api('/api/auth/users', {
          method: 'POST',
          body: JSON.stringify({
            username: $('newUsername').value.trim(),
            password: $('newPassword').value,
            is_admin: $('newRole').value === 'admin',
            device_limit: Number($('newDeviceLimit').value || 1),
            expires_at: $('newExpiresAt').value.trim()
          })
        });
        $('userModal').classList.remove('open');
        $('userForm').reset();
        $('newDeviceLimit').value = '1';
        loadUsers();
      } catch (error) {
        $('userError').textContent = error.message;
      }
    }

    document.addEventListener('click', async (event) => {
      const toggleId = event.target.dataset.toggle;
      const deleteId = event.target.dataset.delete;
      if (toggleId) {
        await api('/api/auth/users/status', { method: 'PUT', body: JSON.stringify({ user_id: Number(toggleId), is_active: event.target.dataset.active === '1' }) });
        loadUsers();
      }
      if (deleteId && confirm('确定删除这个用户？')) {
        await api('/api/auth/users/' + encodeURIComponent(deleteId), { method: 'DELETE' });
        loadUsers();
      }
    });

    $('loginForm').addEventListener('submit', login);
    $('userForm').addEventListener('submit', createUser);
    $('logoutBtn').addEventListener('click', () => { localStorage.removeItem('central_auth_token'); state.token = ''; showLogin(); });
    $('addUserBtn').addEventListener('click', () => $('userModal').classList.add('open'));
    $('cancelUser').addEventListener('click', () => $('userModal').classList.remove('open'));
    $('refreshUsers').addEventListener('click', loadUsers);
    $('refreshActivity').addEventListener('click', loadActivity);
    $('refreshLogins').addEventListener('click', loadLogins);
    $('refreshDevices').addEventListener('click', loadDevices);
    document.querySelectorAll('.nav button').forEach(btn => btn.addEventListener('click', () => switchView(btn.dataset.view)));
    verifySession();
  </script>
</body>
</html>"""
