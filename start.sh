#!/bin/bash
# start.sh - AnotherViewer Web 快速启动脚本（开发/调试模式）
#
# 以 nohup 后台进程直接启动服务。需要 systemd 服务（开机自启、故障自动拉起、
# journal 日志）时请改用 ./install.sh（交互式，可选 --yes 全自动化）。
#
# 用法: ./start.sh [端口]    # 端口可选，默认 8080

set -e

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"

PORT="${1:-8080}"
OS_NAME=$(uname -s)

# ---------------------------------------------------------------------------
# 检测 Java 21+（本项目按 Java 21 编译；Spring Boot 3.x 最低要求 17）
# 优先级：JAVA_HOME → Linux /usr/lib/jvm 或 macOS Homebrew/java_home → 系统 java
# ---------------------------------------------------------------------------
java_major_version() {
    "$1" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"'
}

find_java() {
    # 1. 已设置 JAVA_HOME 且版本足够
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        local ver
        ver=$(java_major_version "$JAVA_HOME/bin/java")
        if [ "${ver:-0}" -ge 21 ] 2>/dev/null; then
            echo "$JAVA_HOME/bin/java"
            return
        fi
    fi

    if [ "$OS_NAME" = "Darwin" ]; then
        # 2. Homebrew openjdk@21 / openjdk@17（优先于 java_home，因为 macOS
        #    的 /usr/libexec/java_home -v 17+ 在无匹配时不报错，会返回旧版 JDK）
        local brew_java
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

        # 3. macOS /usr/libexec/java_home
        if command -v /usr/libexec/java_home &>/dev/null; then
            local jh
            jh=$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)
            if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
                echo "$jh/bin/java"
                return
            fi
        fi
    else
        # 2. Linux：/usr/lib/jvm 标准安装路径（RHEL/Fedora/Debian/Arch）
        local d
        for d in /usr/lib/jvm/java-21-openjdk* \
                 /usr/lib/jvm/jre-21-openjdk* \
                 /usr/lib/jvm/java-17-openjdk* \
                 /usr/lib/jvm/jre-17-openjdk*; do
            if [ -x "$d/bin/java" ]; then
                echo "$d/bin/java"
                return
            fi
        done
    fi

    # 3. 系统 java（最后兜底，可能版本不够）
    if command -v java &>/dev/null; then
        echo "java"
        return
    fi
}

install_hint() {
    if [ "$OS_NAME" = "Darwin" ]; then
        echo "brew install openjdk@21"
    elif command -v dnf &>/dev/null; then
        echo "sudo dnf install -y java-21-openjdk-devel   # RHEL/Fedora"
    elif command -v apt-get &>/dev/null; then
        echo "sudo apt-get install -y openjdk-21-jdk     # Debian/Ubuntu"
    elif command -v pacman &>/dev/null; then
        echo "sudo pacman -S --needed jdk21-openjdk      # Arch Linux"
    else
        echo "用系统包管理器安装 JDK 21"
    fi
}

JAVA_BIN=$(find_java)

if [ -z "$JAVA_BIN" ]; then
    echo "错误: 未找到 Java，本项目按 Java 21 编译，请安装 JDK 21+"
    echo "  安装: $(install_hint)"
    exit 1
fi

# 版本检查（JAR 按 Java 21 编译，更低版本的 JRE 无法加载）
JAVA_VER=$(java_major_version "$JAVA_BIN")
if [ "${JAVA_VER:-0}" -lt 21 ] 2>/dev/null; then
    echo "错误: 当前 Java 版本为 $JAVA_VER，本项目按 Java 21 编译，需要 21+"
    echo "  安装: $(install_hint)"
    exit 1
fi

# ---------------------------------------------------------------------------
# 查找可执行 JAR（排除 -plain.jar）
# ---------------------------------------------------------------------------
JAR_FILE=$(find anotherviewer-web/build/libs -name "anotherviewer-web-*.jar" ! -name "*-plain.jar" 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件，请先运行 ./build.sh"
    exit 1
fi

PID_FILE="anotherviewer-web.pid"
LOG_FILE="anotherviewer-web.log"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "服务已在运行 (PID: $(cat "$PID_FILE"))，访问 http://localhost:$PORT"
    exit 0
fi

echo "=== 启动 AnotherViewer Web ==="
echo "Java: $JAVA_BIN (version $JAVA_VER)"
echo "JAR:  $JAR_FILE"
echo "端口: $PORT"

nohup "$JAVA_BIN" -jar "$JAR_FILE" \
    --server.port="$PORT" \
    --anotherviewer.download.path=./data/downloads \
    --anotherviewer.download.cache-path=./data/cache \
    --anotherviewer.download.worker-count=3 \
    --anotherviewer.download.cache-size-mb=10240 \
    > "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"

for _ in $(seq 1 30); do
    # 200/401/403 说明应答的是本应用；404/000 可能是端口被其他服务占用或尚未启动
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/v1/auth/status" 2>/dev/null || echo 000)
    case "$code" in
        200|401|403)
            echo "服务已就绪: http://localhost:$PORT (PID: $PID)"
            exit 0 ;;
    esac
    sleep 1
done

echo "服务已后台启动 (PID: $PID)，日志: $LOG_FILE"
echo "访问 http://localhost:$PORT 确认，或用 ./stop.sh 停止"
