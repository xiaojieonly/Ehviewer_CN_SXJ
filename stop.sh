#!/bin/bash
# stop.sh - 停止服务

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
