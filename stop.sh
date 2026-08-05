#!/bin/bash
# stop.sh - AnotherViewer Web 停止脚本（systemd 感知）

UNIT_NAME="anotherviewer-web.service"

# 已安装为 systemd 服务时优先经 systemd 停止
if [ -f "/etc/systemd/system/$UNIT_NAME" ] && systemctl is-active --quiet "$UNIT_NAME"; then
    echo "=== 停止 systemd 服务 $UNIT_NAME ==="
    sudo systemctl stop "$UNIT_NAME"
    echo "服务已停止（单元仍保留；移除请用 ./install.sh --uninstall）"
    exit 0
fi

PID_FILE="anotherviewer-web.pid"

stop_pid() {
    local pid="$1"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "=== 停止 AnotherViewer Web (PID: $pid) ==="
        kill "$pid"
        echo "服务已停止"
    else
        echo "服务未运行"
    fi
}

if [ -f "$PID_FILE" ]; then
    stop_pid "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
else
    stop_pid "$(pgrep -f 'java.*anotherviewer-web-.*\.jar' | head -1)"
fi
