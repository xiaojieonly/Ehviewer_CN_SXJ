#!/bin/bash
# stop.sh - AnotherViewer Web 停止脚本（systemd 感知）

UNIT_NAME="anotherviewer-web.service"
STOP_TIMEOUT=30

# sudo 前预检：无法免密获取凭据时先明确失败，避免停止流程中途卡在密码提示
require_sudo() {
    if sudo -n true 2>/dev/null; then return 0; fi
    if [ -t 0 ] && sudo -v; then return 0; fi
    echo "错误: 需要 sudo 权限但无法获取。请先执行 sudo -v 缓存凭据后重试。"
    exit 1
}

# 已安装为 systemd 服务时优先经 systemd 停止
if [ -f "/etc/systemd/system/$UNIT_NAME" ] && systemctl is-active --quiet "$UNIT_NAME"; then
    echo "=== 停止 systemd 服务 $UNIT_NAME ==="
    require_sudo
    sudo systemctl stop "$UNIT_NAME"
    echo "服务已停止（单元仍保留；移除请用 ./install.sh --uninstall）"
    exit 0
fi

PID_FILE="anotherviewer-web.pid"

# SIGTERM 后等待进程退出（≤STOP_TIMEOUT 秒），超时升级 kill -9
stop_pid() {
    local pid="$1" waited=0
    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
        return 1
    fi
    echo "=== 停止 AnotherViewer Web (PID: $pid) ==="
    kill -TERM "$pid" 2>/dev/null || true
    while kill -0 "$pid" 2>/dev/null; do
        if [ "$waited" -ge "$STOP_TIMEOUT" ]; then
            echo "进程 ${STOP_TIMEOUT}s 内未响应 SIGTERM，升级 kill -9"
            kill -9 "$pid" 2>/dev/null || true
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    echo "服务已停止 (PID: $pid)"
}

stopped_any=0
if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if stop_pid "$pid"; then stopped_any=1; fi
    rm -f "$PID_FILE"
fi

# 兜底：pid 文件缺失/失效时按命令行特征匹配；可能存在多个残留进程，全部停止
for pid in $(pgrep -f 'java.*anotherviewer-web-.*\.jar' 2>/dev/null || true); do
    if stop_pid "$pid"; then stopped_any=1; fi
done

if [ "$stopped_any" = 0 ]; then
    echo "服务未运行"
fi
