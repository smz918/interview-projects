#!/usr/bin/env bash
set -euo pipefail

# ================= 动态路径获取 =================
# 获取脚本所在的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 获取项目根目录 (脚本文件夹的上一级)
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
# 虚拟环境 Python 路径 (假设 .venv 在项目根目录下)
PYTHON_BIN="$PROJECT_DIR/.venv/bin/python"
# ===============================================

# --- 配置参数 ---
SERVICE_NAME="fastapi"
SERVICE_PATH="/etc/systemd/system/$SERVICE_NAME.service"
# 自动识别当前真实用户，避免硬编码 root
CURRENT_USER=${SUDO_USER:-$(whoami)}
CURRENT_GROUP=$(id -gn "$CURRENT_USER")

echo "--- 确认动态环境 ---"
echo "项目目录: $PROJECT_DIR"
echo "Python路径: $PYTHON_BIN"
echo "运行用户: $CURRENT_USER"

# 检查是否为 root 运行（写入 systemd 需要 sudo）
if [[ $EUID -ne 0 ]]; then
   echo "请使用 sudo 运行此脚本: sudo ./deploy_fastapi.sh"
   exit 1
fi

# 1. 检查关键文件是否存在
if [ ! -f "$PYTHON_BIN" ]; then
    echo "错误: 找不到虚拟环境中的 Python 解释器: $PYTHON_BIN"
    echo "请确认项目根目录下已建立 .venv 虚拟环境"
    exit 1
fi

if [ ! -f "$PROJECT_DIR/run.py" ]; then
    echo "错误: 找不到启动文件 $PROJECT_DIR/run.py"
    exit 1
fi

echo "--- 2. 写入 Systemd 服务文件 ---"
cat <<EOF > $SERVICE_PATH
[Unit]
Description=FastAPI Digital Person Backend Service
After=network.target

[Service]
User=$CURRENT_USER
Group=$CURRENT_GROUP
WorkingDirectory=$PROJECT_DIR
ExecStart=$PYTHON_BIN run.py
Restart=always
RestartSec=5
# 日志记录在 /var/log 下
StandardOutput=append:/var/log/$SERVICE_NAME.log
StandardError=append:/var/log/$SERVICE_NAME.error.log

[Install]
WantedBy=multi-user.target
EOF

# 确保日志文件存在并有写入权限
touch /var/log/$SERVICE_NAME.log /var/log/$SERVICE_NAME.error.log
chown $CURRENT_USER:$CURRENT_GROUP /var/log/$SERVICE_NAME*.log

# 3. 重新加载配置并启动
echo "--- 3. 重新加载配置并启动服务 ---"
systemctl daemon-reload
systemctl enable $SERVICE_NAME
systemctl restart $SERVICE_NAME

echo "---------------------------------------"
echo "✅ 部署完成！"
echo "服务名称: $SERVICE_NAME"
echo "查看状态: systemctl status $SERVICE_NAME"
echo "启动服务: systemctl start $SERVICE_NAME"
echo "停止服务: systemctl stop $SERVICE_NAME"
echo "重启服务: systemctl restart $SERVICE_NAME"
echo "查看日志: tail -f /var/log/$SERVICE_NAME.log"
echo "---------------------------------------"