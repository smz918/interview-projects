#!/usr/bin/env bash
set -euo pipefail

# ================= 动态路径获取 (用于卸载后的提示) =================
# 获取脚本所在的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 获取项目根目录并锁定资源目录
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_RESOURCES="$PROJECT_ROOT/resources"
# ===============================================================

# ================= 配置区 (需与安装脚本一致) =================
NGINX_VER="1.28.1"
INSTALL_DIR="/opt/nginx-$NGINX_VER"
# ============================================================

# 检查是否为 root
if [[ $EUID -ne 0 ]]; then
   echo "请使用 sudo 运行此脚本: sudo ./uninstall_nginx.sh"
   exit 1
fi

echo "--- 确认卸载环境 ---"
echo "识别到的资源目录: $PROJECT_RESOURCES"

echo "--- 1. 停止并禁用 Nginx 服务 ---"
if systemctl is-active --quiet nginx-dp; then
    systemctl stop nginx-dp
    echo "   已停止 nginx-dp 服务"
fi

if systemctl is-enabled --quiet nginx-dp; then
    systemctl disable nginx-dp
    echo "   已禁用 nginx-dp 开机自启"
fi

echo "--- 2. 移除 Systemd 服务文件 ---"
if [ -f "/etc/systemd/system/nginx-dp.service" ]; then
    rm -f "/etc/systemd/system/nginx-dp.service"
    systemctl daemon-reload
    echo "   已删除 nginx-dp.service"
fi

echo "--- 3. 移除全局软链接 ---"
if [ -L "/usr/local/bin/nginx" ]; then
    rm -f "/usr/local/bin/nginx"
    echo "   已删除 /usr/local/bin/nginx"
fi

echo "--- 4. 删除安装目录 ---"
if [ -d "$INSTALL_DIR" ]; then
    rm -rf "$INSTALL_DIR"
    echo "   已删除安装目录 $INSTALL_DIR"
else
    echo "   未发现安装目录 $INSTALL_DIR，跳过"
fi

echo "--- 5. 清理编译临时文件 ---"
if [ -d "/tmp/nginx_build" ]; then
    rm -rf "/tmp/nginx_build"
    echo "   已清理 /tmp/nginx_build"
fi

echo "--- ✅ 清除完成 ---"
# 这里使用了动态识别的路径，让提示更人性化
echo "提示: 项目资源目录 ($PROJECT_RESOURCES) 下的文件已保留，未做删除。"