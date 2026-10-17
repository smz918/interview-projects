#!/usr/bin/env bash
set -euo pipefail

NGINX_BIN="/opt/nginx-1.28.1/sbin/nginx"

if [ ! -x "$NGINX_BIN" ]; then
    echo "Nginx is not installed at $NGINX_BIN"
    exit 0
fi

if pgrep -x nginx >/dev/null 2>&1; then
    "$NGINX_BIN" -s quit
    echo "Nginx stopped."
else
    echo "Nginx is not running."
fi
