#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_RESOURCES="$PROJECT_ROOT/resources"
NGINX_VER="1.28.1"
INSTALL_DIR="/opt/nginx-$NGINX_VER"
FASTAPI_PORT="8888"
NGINX_BIN="$INSTALL_DIR/sbin/nginx"

echo "--- Nginx container deploy ---"
echo "project: $PROJECT_ROOT"
echo "resources: $PROJECT_RESOURCES"

if [[ $EUID -ne 0 ]]; then
   echo "Please run as root: sudo bash scripts/deploy_nginx_container.sh"
   exit 1
fi

mkdir -p "$PROJECT_RESOURCES"

if [ ! -x "$NGINX_BIN" ]; then
    echo "--- install build dependencies ---"
    apt-get update
    apt-get install -y build-essential libpcre3-dev zlib1g-dev libssl-dev wget

    echo "--- build nginx $NGINX_VER ---"
    BUILD_DIR="/tmp/nginx_build"
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    if [ ! -f "nginx-$NGINX_VER.tar.gz" ]; then
        wget "https://nginx.org/download/nginx-$NGINX_VER.tar.gz"
    fi
    rm -rf "nginx-$NGINX_VER"
    tar -xzf "nginx-$NGINX_VER.tar.gz"
    cd "nginx-$NGINX_VER"
    ./configure --prefix="$INSTALL_DIR" \
        --with-http_ssl_module \
        --with-http_v2_module \
        --with-http_gzip_static_module
    make -j"$(nproc)"
    make install
fi

mkdir -p "$INSTALL_DIR/conf/sites-enabled"
if ! grep -q "sites-enabled" "$INSTALL_DIR/conf/nginx.conf"; then
    sed -i '/http {/a \    include sites-enabled/*.conf;' "$INSTALL_DIR/conf/nginx.conf"
fi

cat > "$INSTALL_DIR/conf/sites-enabled/digital_person.conf" <<EOF
server {
    listen 80;
    server_name localhost 127.0.0.1 _;

    client_max_body_size 100m;

    location /resources/ {
        alias $PROJECT_RESOURCES/;
        expires 7d;
        add_header Access-Control-Allow-Origin '*' always;
        add_header Accept-Ranges bytes;
    }

    location / {
        if (\$request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' '*' always;
            add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS' always;
            add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization' always;
            return 204;
        }

        add_header 'Access-Control-Allow-Origin' '*' always;
        proxy_pass http://127.0.0.1:$FASTAPI_PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
EOF

chmod -R 755 "$PROJECT_RESOURCES"
"$NGINX_BIN" -t

if "$NGINX_BIN" -s reload >/dev/null 2>&1; then
    echo "Nginx reloaded."
else
    "$NGINX_BIN"
    echo "Nginx started."
fi

ln -sf "$NGINX_BIN" /usr/local/bin/nginx
echo "check: curl http://127.0.0.1/"
