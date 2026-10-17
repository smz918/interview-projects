#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="$PROJECT_DIR/logs/fastapi.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "FastAPI pid file not found."
    exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [ -n "$PID" ] && kill -0 "$PID" >/dev/null 2>&1; then
    kill "$PID"
    echo "FastAPI stopped: $PID"
else
    echo "FastAPI is not running."
fi

rm -f "$PID_FILE"
