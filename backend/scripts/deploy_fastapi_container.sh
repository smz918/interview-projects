#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PYTHON_BIN="$PROJECT_DIR/.venv/bin/python"
PID_FILE="$PROJECT_DIR/logs/fastapi.pid"
LOG_FILE="$PROJECT_DIR/logs/fastapi.out.log"

cd "$PROJECT_DIR"
mkdir -p "$PROJECT_DIR/logs"

echo "--- FastAPI container/nohup deploy ---"
echo "project: $PROJECT_DIR"

if command -v uv >/dev/null 2>&1; then
    echo "--- sync dependencies ---"
    uv sync
elif [ ! -x "$PYTHON_BIN" ]; then
    echo "uv is not installed and .venv is missing."
    echo "Install uv first: curl -LsSf https://astral.sh/uv/install.sh | sh"
    exit 1
fi

if [ ! -x "$PYTHON_BIN" ]; then
    echo "Python not found: $PYTHON_BIN"
    exit 1
fi

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE" || true)"
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
        echo "--- stop old FastAPI process: $OLD_PID ---"
        kill "$OLD_PID"
        sleep 2
    fi
fi

echo "--- start FastAPI ---"
nohup "$PYTHON_BIN" run.py >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "FastAPI started."
echo "pid: $(cat "$PID_FILE")"
echo "log: $LOG_FILE"
echo "check: tail -f $LOG_FILE"
