#!/bin/bash
# start.sh - 启动服务

set -e

JAR_FILE=$(find ehviewer-web/build/libs -name "ehviewer-web-*.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件，请先运行 ./build.sh"
    exit 1
fi

echo "=== 启动 EhViewer Web ==="
echo "JAR: $JAR_FILE"
echo "端口: 8080"

java -jar "$JAR_FILE" \
    --server.port=8080 \
    --ehviewer.download.path=./data/downloads \
    --ehviewer.download.cache-path=./data/cache \
    --ehviewer.download.worker-count=3 \
    --ehviewer.cache.size-mb=10240
