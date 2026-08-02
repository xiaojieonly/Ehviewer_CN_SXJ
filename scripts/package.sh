#!/bin/bash
# package.sh - 构建官方 zip 发布包
#
# 产出 anotherviewer-<version>-<os>-<arch>.zip：
#   lib/app.jar    服务主体（可执行 fat jar）
#   bin/start.sh   启动脚本（脚本所在目录推导，默认 data-dir = 解压目录 data/）
#   bin/stop.sh    停止脚本
#   data/          预建数据目录模板（anotherviewer.db/security.key/downloads/cache/backups）
#   README.txt     安装说明
#
# 用法:
#   package.sh [-v <version>] [-o <outdir>] [--no-data] [--jar <path>]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTDIR="$REPO_ROOT/dist"
VERSION=""
JAR_PATH=""
BUILD_DATA=1

usage() {
    cat <<'EOF'
用法: package.sh [-v <version>] [-o <outdir>] [--no-data] [--jar <path>]

  -v <version>   版本号（缺省：gradle.properties → anotherviewer-web/build.gradle.kts → jar 文件名）
  -o <outdir>    输出目录（默认 ./dist）
  --no-data      不预建 data/ 模板
  --jar <path>   指定 jar 路径（默认 anotherviewer-web/build/libs/*.jar，排除 -plain.jar）
  -h, --help     显示帮助
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -v)
            VERSION="$2"
            shift 2
            ;;
        -o)
            OUTDIR="$2"
            shift 2
            ;;
        --no-data)
            BUILD_DATA=0
            shift
            ;;
        --jar)
            JAR_PATH="$2"
            shift 2
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            echo "错误: 未知参数 $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# 版本推导：-v 参数 → gradle.properties → build.gradle.kts → jar 文件名
# ---------------------------------------------------------------------------
if [ -z "$VERSION" ]; then
    VERSION=$(grep -E '^version[[:space:]]*=' "$REPO_ROOT/gradle.properties" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '[:space:]' || true)
fi
if [ -z "$VERSION" ]; then
    VERSION=$(sed -nE 's/^version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$REPO_ROOT/anotherviewer-web/build.gradle.kts" 2>/dev/null | head -1 || true)
fi

# ---------------------------------------------------------------------------
# JAR 查找
# ---------------------------------------------------------------------------
if [ -z "$JAR_PATH" ]; then
    JAR_PATH=$(find "$REPO_ROOT/anotherviewer-web/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' 2>/dev/null | head -1 || true)
fi
if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    echo "错误: 未找到可执行 jar，请先构建: ./gradlew :anotherviewer-web:bootJar" >&2
    exit 1
fi
JAR_PATH="$(cd "$(dirname "$JAR_PATH")" && pwd)/$(basename "$JAR_PATH")"

if [ -z "$VERSION" ]; then
    VERSION=$(basename "$JAR_PATH" | sed -E 's/^anotherviewer-web-(.+)\.jar$/\1/')
fi

# ---------------------------------------------------------------------------
# os / arch 推导（uname 输出小写化；darwin→darwin, x86_64→x86_64）
# ---------------------------------------------------------------------------
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m | tr '[:upper:]' '[:lower:]')

ZIP_NAME="anotherviewer-$VERSION-$OS-$ARCH.zip"
STAGE="$OUTDIR/.stage-$$"

mkdir -p "$STAGE/lib" "$STAGE/bin"
cp "$JAR_PATH" "$STAGE/lib/app.jar"

cat > "$STAGE/bin/start.sh" <<'EOF'
#!/bin/bash
# start.sh - 启动 AnotherViewer Web（zip 发布包版）

set -e

# 脚本所在目录推导（解压到任意路径均可用）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_FILE="$APP_HOME/lib/app.jar"
DATA_DIR="$APP_HOME/data"
PID_FILE="$APP_HOME/anotherviewer-web.pid"
LOG_FILE="$APP_HOME/anotherviewer-web.log"

# --data-dir <路径> 透传 java（缺省 = 解压目录 data/）
while [ $# -gt 0 ]; do
    case "$1" in
        --data-dir)
            DATA_DIR="$2"
            shift 2
            ;;
        --data-dir=*)
            DATA_DIR="${1#*=}"
            shift
            ;;
        *)
            echo "错误: 未知参数 $1（仅支持 --data-dir <路径>）" >&2
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# 检测 Java 21（优先级：JAVA_HOME → Homebrew → java_home → 系统 java）
# ---------------------------------------------------------------------------
find_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        local ver
        ver=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
        if [ "${ver:-0}" -ge 21 ] 2>/dev/null; then
            echo "$JAVA_HOME/bin/java"
            return
        fi
    fi

    for brew_java in \
        /opt/homebrew/opt/openjdk@21/bin/java \
        /usr/local/opt/openjdk@21/bin/java; do
        if [ -x "$brew_java" ]; then
            echo "$brew_java"
            return
        fi
    done

    if command -v /usr/libexec/java_home &>/dev/null; then
        local jh
        jh=$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)
        if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
            local ver
            ver=$("$jh/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
            if [ "${ver:-0}" -ge 21 ] 2>/dev/null; then
                echo "$jh/bin/java"
                return
            fi
        fi
    fi

    if command -v java &>/dev/null; then
        echo "java"
        return
    fi
}

JAVA_BIN=$(find_java)
if [ -z "$JAVA_BIN" ]; then
    echo "错误: 未找到 Java，请安装 JDK 21（brew install openjdk@21 或发行版 openjdk-21）" >&2
    exit 1
fi

JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
if [ "${JAVA_VER:-0}" -lt 21 ] 2>/dev/null; then
    echo "错误: 当前 Java 版本为 $JAVA_VER，需要 Java 21+" >&2
    exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 未找到 $JAR_FILE" >&2
    exit 1
fi

mkdir -p "$DATA_DIR"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "服务已在运行 (PID: $(cat "$PID_FILE"))，访问 http://localhost:8080"
    exit 0
fi

echo "=== 启动 AnotherViewer Web ==="
echo "Java:    $JAVA_BIN (version $JAVA_VER)"
echo "JAR:     $JAR_FILE"
echo "data-dir: $DATA_DIR"
echo "端口:    8080"

nohup "$JAVA_BIN" -jar "$JAR_FILE" --data-dir="$DATA_DIR" > "$LOG_FILE" 2>&1 &
PID=$!
echo "$PID" > "$PID_FILE"

for _ in $(seq 1 30); do
    if curl -s -o /dev/null http://localhost:8080/api/v1/auth/status; then
        echo "服务已就绪: http://localhost:8080 (PID: $PID)"
        exit 0
    fi
    sleep 1
done

echo "服务已后台启动 (PID: $PID)，日志: $LOG_FILE"
echo "访问 http://localhost:8080 确认，或用 bin/stop.sh 停止"
EOF

cat > "$STAGE/bin/stop.sh" <<'EOF'
#!/bin/bash
# stop.sh - 停止 AnotherViewer Web（zip 发布包版）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$APP_HOME/anotherviewer-web.pid"

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
    stop_pid "$(pgrep -f 'lib/app\.jar' | head -1)"
fi
EOF

chmod +x "$STAGE/bin/start.sh" "$STAGE/bin/stop.sh"

cat > "$STAGE/README.txt" <<EOF
AnotherViewer Web $VERSION ($OS/$ARCH)

安装说明
1. 解压本包到任意目录（例如 /opt/anotherviewer）
2. 依赖 Java 21（JRE 即可）：
   - Linux (Debian/Ubuntu):  sudo apt install openjdk-21-jre
   - Linux (Fedora/RHEL):    sudo dnf install java-21-openjdk
   - macOS:                  brew install openjdk@21
3. 启动：bin/start.sh；停止：bin/stop.sh
4. 访问 http://localhost:8080（首次使用请在 WebUI 完成配对）

目录结构
  lib/app.jar   服务主体
  bin/          启动 / 停止脚本
  data/         数据目录（固定结构见 data/README.md）
  README.txt    本说明

--data-dir 语义
默认数据目录为解压目录下的 data/；启动脚本支持覆盖，例如：
  bin/start.sh --data-dir /var/lib/anotherviewer
数据目录固定结构：anotherviewer.db / security.key / downloads/ / cache/ / backups/。

systemd（可选，仅 Linux）
仓库 packaging/systemd/anotherviewer.service.tpl 提供了 systemd unit 模板，
安装到 /lib/systemd/system/anotherviewer.service 后：
  sudo systemctl daemon-reload && sudo systemctl enable --now anotherviewer
EOF

if [ "$BUILD_DATA" -eq 1 ]; then
    mkdir -p "$STAGE/data/downloads" "$STAGE/data/cache" "$STAGE/data/backups"
    cat > "$STAGE/data/README.md" <<'EOF'
# data/（data-dir）固定结构

本目录是 AnotherViewer Web 的权威数据目录，固定结构如下：

- anotherviewer.db     SQLite 主数据库（用户数据、收藏、历史、下载记录）
- security.key    WebUI 配对/设备认证密钥
- downloads/      下载文件
- cache/          下载缓存
- backups/        备份产物（7z 分片 + manifest）

由 bin/start.sh 默认使用；可通过 --data-dir <路径> 指向其他位置
（发行版包安装场景下为 /var/lib/anotherviewer）。
EOF
fi

mkdir -p "$OUTDIR"
(
    cd "$STAGE"
    zip -qr "$OUTDIR/$ZIP_NAME" .
)
rm -rf "$STAGE"

echo "已生成: $OUTDIR/$ZIP_NAME"
