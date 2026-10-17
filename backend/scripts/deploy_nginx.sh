#!/usr/bin/env bash
set -euo pipefail

# ================= 动态路径获取 =================
# 获取脚本所在的绝对路径 (例如: ...backend-4-20-d/script)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 获取项目根目录 (脚本文件夹的上一级)
# 例如: .../backend-4-20-d
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 动态锁定资源目录
PROJECT_RESOURCES="$PROJECT_ROOT/resources"
# ===============================================

# ================= 配置区 =================
NGINX_VER="1.28.1"
INSTALL_DIR="/opt/nginx-$NGINX_VER"
FASTAPI_PORT="8888"

# 识别真实的执行用户
CURRENT_USER=${SUDO_USER:-$(whoami)}
# 自动获取该用户的 home 目录
USER_HOME=$(getent passwd "$CURRENT_USER" | cut -d: -f6)
# ==========================================

if [[ $EUID -ne 0 ]]; then
   echo "请使用 sudo 运行此脚本: sudo ./deploy_nginx.sh"
   exit 1
fi

echo "--- 确认动态路径 ---"
echo "脚本位置: $SCRIPT_DIR"
echo "项目根目录: $PROJECT_ROOT"
echo "资源目录: $PROJECT_RESOURCES"
echo "实际执行用户: $CURRENT_USER"
echo "用户家目录: $USER_HOME"

# 检查资源目录是否存在，防止路径计算错误
if [ ! -d "$PROJECT_RESOURCES" ]; then
    echo "错误: 未能找到资源目录 $PROJECT_RESOURCES"
    exit 1
fi

echo "--- 1. 安装编译依赖 ---"
apt-get update
apt-get install -y build-essential libpcre3-dev zlib1g-dev libssl-dev wget

echo "--- 2. 下载并编译 Nginx ---"
BUILD_DIR="/tmp/nginx_build"
mkdir -p "$BUILD_DIR" && cd "$BUILD_DIR"
if [ ! -f "nginx-$NGINX_VER.tar.gz" ]; then
    wget "https://nginx.org/download/nginx-$NGINX_VER.tar.gz"
fi
tar -xzf "nginx-$NGINX_VER.tar.gz"
cd "nginx-$NGINX_VER"

./configure --prefix="$INSTALL_DIR" \
    --with-http_ssl_module \
    --with-http_v2_module \
    --with-http_gzip_static_module \
    --user="$CURRENT_USER"

make -j"$(nproc)" && make install

echo "--- 3. 配置 Nginx 主文件 (设置运行用户) ---"
sed -i "1s|^.*$|user $CURRENT_USER;|" "$INSTALL_DIR/conf/nginx.conf"

echo "--- 4. 创建项目专用站点配置 ---"
mkdir -p "$INSTALL_DIR/conf/sites-enabled"
if ! grep -q "sites-enabled" "$INSTALL_DIR/conf/nginx.conf"; then
    sed -i '/http {/a \    include sites-enabled/*.conf;' "$INSTALL_DIR/conf/nginx.conf"
fi

cat > "$INSTALL_DIR/conf/sites-enabled/digital_person.conf" <<EOF
server {
    listen 80;
    server_name localhost 127.0.0.1;

    # 动态生成的静态资源路径
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
    }
}
EOF

echo "--- 5. 修正目录穿透权限 ---"
chmod +x "$USER_HOME"
chmod -R 755 "$PROJECT_RESOURCES"

echo "--- 6. 创建 Systemd 服务 ---"
cat > "/etc/systemd/system/nginx-dp.service" <<EOF
[Unit]
Description=Nginx for Digital Person
After=network.target

[Service]
Type=forking
ExecStart=$INSTALL_DIR/sbin/nginx
ExecReload=$INSTALL_DIR/sbin/nginx -s reload
ExecStop=$INSTALL_DIR/sbin/nginx -s quit
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable nginx-dp
systemctl restart nginx-dp

ln -sf "$INSTALL_DIR/sbin/nginx" /usr/local/bin/nginx

echo "---------------------------------------"
echo "✅ 部署完成！"
echo "Nginx 运行用户: \$(ps aux | grep 'nginx: worker process' | grep -v grep | awk '{print $1}' | head -n 1)"
echo "项目资源目录: $PROJECT_RESOURCES"
echo "查看状态: systemctl status nginx-dp"
echo "启动服务: systemctl start nginx-dp"
echo "停止服务: systemctl stop nginx-dp"
echo "重启服务: systemctl restart nginx-dp"
echo "---------------------------------------"
