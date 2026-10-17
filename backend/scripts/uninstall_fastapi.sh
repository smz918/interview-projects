#!/bin/bash

# --- 配置参数 ---
SERVICE_NAME="fastapi"
SERVICE_PATH="/etc/systemd/system/$SERVICE_NAME.service"

echo "正在准备销毁 $SERVICE_NAME 服务..."

# 1. 停止并禁用服务
if systemctl is-active --quiet $SERVICE_NAME; then
    systemctl stop $SERVICE_NAME
    echo "已停止运行中的服务。"
fi

systemctl disable $SERVICE_NAME

# 2. 删除服务文件
if [ -f "$SERVICE_PATH" ]; then
    rm "$SERVICE_PATH"
    echo "已删除系统服务文件: $SERVICE_PATH"
fi

# 3. 重载系统配置
systemctl daemon-reload

# 4. (可选) 删除日志文件
rm -f /var/log/$SERVICE_NAME.log /var/log/$SERVICE_NAME.error.log

echo "---------------------------------------"
echo "销毁完成！服务已从系统中移除。"
echo "---------------------------------------"