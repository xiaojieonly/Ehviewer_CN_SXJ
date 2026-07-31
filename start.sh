#!/bin/bash
# start.sh - 启动服务

set -e

# ---------------------------------------------------------------------------
# 检测 Java 17+（Spring Boot 3.x 最低要求）
# 优先级：JAVA_HOME → /usr/libexec/java_home → Homebrew openjdk → 系统 java
# ---------------------------------------------------------------------------
find_java() {
    # 1. 已设置 JAVA_HOME 且版本 ≥ 17
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        local ver
        ver=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
        if [ "${ver:-0}" -ge 17 ] 2>/dev/null; then
            echo "$JAVA_HOME/bin/java"
            return
        fi
    fi

    # 2. Homebrew openjdk@21 / openjdk@17（优先于 java_home，因为 macOS
    #    的 /usr/libexec/java_home -v 17+ 在无匹配时不报错，会返回旧版 JDK）
    for brew_java in \
        /opt/homebrew/opt/openjdk@21/bin/java \
        /opt/homebrew/opt/openjdk@17/bin/java \
        /usr/local/opt/openjdk@21/bin/java \
        /usr/local/opt/openjdk@17/bin/java; do
        if [ -x "$brew_java" ]; then
            echo "$brew_java"
            return
        fi
    done

    # 3. macOS /usr/libexec/java_home（需验证返回的版本确实 ≥ 17）
    if command -v /usr/libexec/java_home &>/dev/null; then
        local jh
        jh=$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)
        if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
            local ver
            ver=$("$jh/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
            if [ "${ver:-0}" -ge 17 ] 2>/dev/null; then
                echo "$jh/bin/java"
                return
            fi
        fi
    fi

    # 4. 系统 java（最后兜底，可能版本不够）
    if command -v java &>/dev/null; then
        echo "java"
        return
    fi
}

JAVA_BIN=$(find_java)

if [ -z "$JAVA_BIN" ]; then
    echo "错误: 未找到 Java，请安装 JDK 17+（推荐 brew install openjdk@21）"
    exit 1
fi

# 版本检查
JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
if [ "${JAVA_VER:-0}" -lt 17 ] 2>/dev/null; then
    echo "错误: 当前 Java 版本为 $JAVA_VER，Spring Boot 3.x 需要 Java 17+"
    echo "  请安装: brew install openjdk@21"
    echo "  或设置: export JAVA_HOME=\$(/usr/libexec/java_home -v 17+)"
    exit 1
fi

# ---------------------------------------------------------------------------
# 查找可执行 JAR（排除 -plain.jar）
# ---------------------------------------------------------------------------
JAR_FILE=$(find ehviewer-web/build/libs -name "ehviewer-web-*.jar" ! -name "*-plain.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件，请先运行 ./build.sh"
    exit 1
fi

echo "=== 启动 AnotherViewer Web ==="
echo "Java: $JAVA_BIN (version $JAVA_VER)"
echo "JAR:  $JAR_FILE"
echo "端口: 8080"

exec "$JAVA_BIN" -jar "$JAR_FILE" \
    --server.port=8080 \
    --ehviewer.download.path=./data/downloads \
    --ehviewer.download.cache-path=./data/cache \
    --ehviewer.download.worker-count=3 \
    --ehviewer.cache.size-mb=10240
