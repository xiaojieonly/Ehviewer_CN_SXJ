#!/bin/bash
# stop.sh - 停止服务

PID=$(pgrep -f "ehviewer-web-*.jar")

if [ -n "$PID" ]; then
    echo "=== 停止 EhViewer Web (PID: $PID) ==="
    kill "$PID"
    echo "服务已停止"
else
    echo "服务未运行"
fi
