"""Regression checks use a temporary SQLite DB, never the deployed database."""
import tempfile
from pathlib import Path
import importlib.util
from fastapi import HTTPException
from sqlmodel import SQLModel, create_engine

spec = importlib.util.spec_from_file_location("api_under_test", "/app/main.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
with tempfile.TemporaryDirectory() as folder:
    m.engine = create_engine("sqlite:///" + str(Path(folder) / "test.db"))
    SQLModel.metadata.create_all(m.engine)
    m.admin_ok = lambda token: token == "test-only"
    a = m.UserRequest(phone="test100001", password="YOUR_PASSWORD_HERE", device_id="test-device")
    m.user_register(a)
    token = m.user_login(a)["token"]
    m.set_status("test-device", "frozen", "test-only")
    assert m.heartbeat("test-device", m.HeartbeatRequest(), "Bearer " + token)["status"] == "frozen"
    try:
        m.user_login(a)
        raise AssertionError("Login bypassed freeze")
    except HTTPException as e:
        assert e.status_code == 403
    m.set_status("test-device", "active", "test-only")
    new_token = m.user_login(a)["token"]
    assert m.heartbeat("test-device", m.HeartbeatRequest(), "Bearer " + new_token)["status"] == "active"
    try:
        m.heartbeat("test-device", m.HeartbeatRequest(), "Bearer " + token)
        raise AssertionError("Old token still accepted")
    except HTTPException as e:
        assert e.status_code == 401
    b = m.UserRequest(phone="test100002", password="YOUR_PASSWORD_HERE", device_id="test-device")
    m.user_register(b)
    try:
        m.user_login(b)
        raise AssertionError("Account binding bypassed")
    except HTTPException as e:
        assert e.status_code == 403
print("PASS: freeze survives login; restore; token rotation; account binding")
