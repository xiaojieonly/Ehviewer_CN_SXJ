#!/bin/bash
# install.sh - AnotherViewer Web 部署安装脚本（交互式）
#
# 交互式决定服务的运行方式并完成安装：
#   1. systemd 服务 —— 按当前环境动态生成单元文件，安装、开机自启、启动
#   2. 后台进程     —— 开发/调试模式，等同 ./start.sh
#
# systemd 单元基于实际探测到的 Java、JAR、数据目录生成，写入
# /etc/systemd/system/anotherviewer-web.service，可随时重跑（幂等覆盖）。
#
# 非交互模式（脚本化/自动化）：
#   ./install.sh --yes                              # 全部取默认值
#   ./install.sh --yes --service-user anotherviewer --create-user --port 9090
#
# 选项：
#   --systemd            直接以 systemd 服务方式运行（跳过运行方式提问）
#   --nohup              直接以后台进程方式运行（跳过运行方式提问，等同 ./start.sh）
#   --service-user USER  systemd 运行用户（默认：anotherviewer 已存在则用它，否则当前用户）
#   --create-user        服务用户不存在时自动创建为系统用户
#   --port PORT          服务端口（默认 8080）
#   --data-dir DIR       数据目录（默认：<仓库根>/data）
#   --no-build           JAR 缺失时不自动构建
#   --firewall           在 firewalld 中放开服务端口
#   --no-firewall        不改动防火墙
#   --yes                非交互模式：所有提问直接取默认值与命令行参数
#   --uninstall          移除 systemd 服务（不删除数据目录）
#   -h, --help           显示本帮助
#
# 依赖：Java 缺失或版本不足 21 时按发行版经包管理器安装
#   （RHEL/Fedora: dnf；Debian/Ubuntu: apt；Arch Linux: pacman）。
#   应用本身不经包管理器分发，仅安装运行依赖。

set -e

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"

UNIT_NAME="anotherviewer-web.service"
UNIT_PATH="/etc/systemd/system/$UNIT_NAME"

RUN_MODE=""
SERVICE_USER=""
CREATE_USER=0
PORT=8080
DATA_DIR="$ROOT_DIR/data"
AUTO_BUILD=1
FIREWALL=""
YES=0
UNINSTALL=0

usage() {
    awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "$0"
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --systemd) RUN_MODE=systemd ;;
        --nohup) RUN_MODE=nohup ;;
        --service-user) SERVICE_USER="$2"; shift ;;
        --create-user) CREATE_USER=1 ;;
        --port) PORT="$2"; shift ;;
        --data-dir) DATA_DIR="$2"; shift ;;
        --no-build) AUTO_BUILD=0 ;;
        --firewall) FIREWALL=1 ;;
        --no-firewall) FIREWALL=0 ;;
        --yes) YES=1 ;;
        --uninstall) UNINSTALL=1 ;;
        -h|--help) usage ;;
        *) echo "未知参数: $1（--help 查看用法）"; exit 1 ;;
    esac
    shift
done

INTERACTIVE=0
if [ "$YES" = 0 ] && [ -t 0 ]; then INTERACTIVE=1; fi

# 带默认值的提问；非交互模式直接返回默认值。结果经 stdout 返回。
ask() {
    local prompt="$1" default="$2" ans=""
    if [ "$INTERACTIVE" = 1 ]; then
        read -r -p "$prompt [$default]: " ans </dev/tty || ans=""
    fi
    printf '%s' "${ans:-$default}"
}

require_sudo() {
    if sudo -n true 2>/dev/null; then return 0; fi
    if [ "$INTERACTIVE" = 1 ] && sudo -v; then return 0; fi
    echo "错误: 需要 sudo 权限但无法获取。"
    echo "  交互式终端请直接运行 ./install.sh 并输入密码；"
    echo "  或先执行 sudo -v 缓存凭据后重试。"
    exit 1
}

# ---------------------------------------------------------------------------
# Java 探测（与 start.sh 相同的探测链）
# ---------------------------------------------------------------------------
OS_NAME=$(uname -s)

java_major_version() {
    "$1" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | head -1 | tr -d '"'
}

find_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        local ver
        ver=$(java_major_version "$JAVA_HOME/bin/java")
        if [ "${ver:-0}" -ge 21 ] 2>/dev/null; then
            echo "$JAVA_HOME/bin/java"
            return
        fi
    fi

    if [ "$OS_NAME" = "Darwin" ]; then
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

        if command -v /usr/libexec/java_home &>/dev/null; then
            local jh
            jh=$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)
            if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
                echo "$jh/bin/java"
                return
            fi
        fi
    else
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

# ---------------------------------------------------------------------------
# 依赖安装：Java 21+ 缺失时按发行版经包管理器安装
# ---------------------------------------------------------------------------
detect_pkg_manager() {
    if [ -r /etc/os-release ]; then
        # shellcheck disable=SC1091
        . /etc/os-release
        case "$ID ${ID_LIKE:-}" in
            *fedora*|*rhel*) echo dnf; return ;;
            *debian*|*ubuntu*) echo apt; return ;;
            *arch*) echo pacman; return ;;
        esac
    fi
    if command -v dnf &>/dev/null; then echo dnf; return; fi
    if command -v apt-get &>/dev/null; then echo apt; return; fi
    if command -v pacman &>/dev/null; then echo pacman; return; fi
    echo ""
}

ensure_java() {
    JAVA_BIN=$(find_java)
    if [ -n "$JAVA_BIN" ]; then
        JAVA_VER=$(java_major_version "$JAVA_BIN")
        if [ "${JAVA_VER:-0}" -ge 21 ] 2>/dev/null; then
            return 0
        fi
        echo "探测到的 Java 版本为 $JAVA_VER（$JAVA_BIN），本项目按 Java 21 编译，需要 21+"
    else
        echo "未探测到 Java，本项目按 Java 21 编译，需要 21+"
    fi

    if [ "$OS_NAME" = "Darwin" ]; then
        echo "错误: 请手动安装 JDK 21（brew install openjdk@21）后重跑"
        exit 1
    fi

    local pm
    pm=$(detect_pkg_manager)
    if [ -z "$pm" ]; then
        echo "错误: 未识别的包管理器，请手动安装 JDK 21 后重跑"
        echo "  参考: $(install_hint)"
        exit 1
    fi

    local pkg install_cmd
    case "$pm" in
        dnf)    pkg="java-21-openjdk-devel"; install_cmd="sudo dnf install -y $pkg" ;;
        apt)    pkg="openjdk-21-jdk";        install_cmd="sudo apt-get update && sudo apt-get install -y $pkg" ;;
        pacman) pkg="jdk21-openjdk";         install_cmd="sudo pacman -S --needed --noconfirm $pkg" ;;
    esac

    echo ""
    echo "将通过包管理器安装 JDK 21（$pkg）:"
    echo "  $install_cmd"
    local ans
    ans=$(ask "确认安装？(y/n)" "y")
    case "$ans" in
        y|Y) ;;
        *) echo "已中止；请手动安装 JDK 21 后重跑"; exit 1 ;;
    esac

    require_sudo
    if ! eval "$install_cmd"; then
        echo "错误: $pkg 安装失败，请检查上方包管理器输出"
        exit 1
    fi

    JAVA_BIN=$(find_java)
    if [ -z "$JAVA_BIN" ]; then
        echo "错误: 安装结束但仍未探测到 Java，请检查包管理器输出"
        exit 1
    fi
    JAVA_VER=$(java_major_version "$JAVA_BIN")
    if [ "${JAVA_VER:-0}" -lt 21 ] 2>/dev/null; then
        echo "错误: 安装后 Java 版本仍为 $JAVA_VER（$JAVA_BIN）。"
        echo "  Debian 12 注意: openjdk-21 位于 bookworm-backports；或将 JAVA_HOME 指向 JDK 21 后重试"
        exit 1
    fi
    echo "Java 就绪: $JAVA_BIN (version $JAVA_VER)"
}

# ---------------------------------------------------------------------------
# --uninstall：移除 systemd 服务
# ---------------------------------------------------------------------------
if [ "$UNINSTALL" = 1 ]; then
    if [ ! -f "$UNIT_PATH" ]; then
        echo "未安装 systemd 服务（$UNIT_PATH 不存在），无需操作"
        exit 0
    fi
    if [ "$INTERACTIVE" = 1 ]; then
        read -r -p "确认移除 $UNIT_NAME？数据目录不会被删除 (y/N): " ans </dev/tty || ans=""
        case "$ans" in y|Y) ;; *) echo "已取消"; exit 0 ;; esac
    fi
    require_sudo
    sudo systemctl disable --now "$UNIT_NAME" 2>/dev/null || true
    sudo rm -f "$UNIT_PATH"
    sudo systemctl daemon-reload
    echo "已移除 systemd 服务。数据目录保持不变。"
    exit 0
fi

# ---------------------------------------------------------------------------
# 1. 选择运行方式
# ---------------------------------------------------------------------------
if [ -z "$RUN_MODE" ]; then
    echo "请选择 AnotherViewer Web 的运行方式:"
    echo "  1) systemd 服务（推荐：开机自启、故障自动拉起、journal 日志）"
    echo "  2) 后台进程（开发/调试模式，等同 ./start.sh）"
    choice=$(ask "请输入 1 或 2" "1")
    case "$choice" in
        1|systemd) RUN_MODE=systemd ;;
        2|nohup|dev) RUN_MODE=nohup ;;
        *) echo "无效选择: $choice"; exit 1 ;;
    esac
fi

if [ "$RUN_MODE" = "nohup" ]; then
    echo "转交 ./start.sh 启动..."
    exec ./start.sh "$PORT"
fi

# ---------------------------------------------------------------------------
# 2. systemd 方式：确认参数
# ---------------------------------------------------------------------------
if [ -z "$SERVICE_USER" ] && [ "$INTERACTIVE" = 1 ]; then
    exists_note=""
    id anotherviewer &>/dev/null || exists_note="（不存在将自动创建）"
    echo ""
    echo "systemd 运行用户:"
    echo "  1) 专用系统用户 anotherviewer$exists_note（推荐：权限隔离）"
    echo "  2) 当前用户 $(id -un)"
    choice=$(ask "请输入 1 或 2" "1")
    case "$choice" in
        1) SERVICE_USER=anotherviewer ;;
        2) SERVICE_USER=$(id -un) ;;
        *) SERVICE_USER="$choice" ;;
    esac
fi
if [ -z "$SERVICE_USER" ]; then
    if id anotherviewer &>/dev/null; then
        SERVICE_USER=anotherviewer
    else
        SERVICE_USER=$(id -un)
    fi
fi

PORT=$(ask "服务端口" "$PORT")
DATA_DIR=$(ask "数据目录" "$DATA_DIR")
case "$DATA_DIR" in
    /*) ;;
    *) DATA_DIR="$ROOT_DIR/$DATA_DIR" ;;
esac

# ---------------------------------------------------------------------------
# 3. 确保 Java 21+（缺失/版本不足时经包管理器安装）
# ---------------------------------------------------------------------------
ensure_java

# ---------------------------------------------------------------------------
# 4. JAR：缺失则默认自动构建
# ---------------------------------------------------------------------------
# 多次构建后 libs 下可能残留多版本产物，取 mtime 最新的一个（find|head 顺序不定）
JAR_FILE=$(ls -t anotherviewer-web/build/libs/anotherviewer-web-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)

if [ -z "$JAR_FILE" ]; then
    if [ "$AUTO_BUILD" = 1 ]; then
        ans=$(ask "未找到 JAR，现在运行 ./build.sh 构建？(y/n)" "y")
        case "$ans" in
            y|Y) ./build.sh ;;
            *) echo "已中止: 没有可运行的 JAR"; exit 1 ;;
        esac
        JAR_FILE=$(ls -t anotherviewer-web/build/libs/anotherviewer-web-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)
    fi
    if [ -z "$JAR_FILE" ]; then
        echo "错误: 未找到 JAR 文件，请先运行 ./build.sh"
        exit 1
    fi
fi

JAR_FILE=$(cd "$(dirname "$JAR_FILE")" && pwd)/$(basename "$JAR_FILE")

# ---------------------------------------------------------------------------
# 5. 服务用户与数据目录
# ---------------------------------------------------------------------------
if ! id "$SERVICE_USER" &>/dev/null; then
    if [ "$CREATE_USER" = 0 ] && [ "$YES" = 0 ]; then
        ans=$(ask "用户 $SERVICE_USER 不存在，创建为系统用户？(y/n)" "y")
        case "$ans" in
            y|Y) CREATE_USER=1 ;;
            *) echo "已中止"; exit 1 ;;
        esac
    else
        CREATE_USER=1
    fi
fi

if [ "$CREATE_USER" = 1 ] && ! id "$SERVICE_USER" &>/dev/null; then
    require_sudo
    sudo useradd -r -s /usr/sbin/nologin -d "$ROOT_DIR" "$SERVICE_USER"
    echo "已创建系统用户: $SERVICE_USER"
fi

mkdir -p "$DATA_DIR"
if [ "$SERVICE_USER" != "$(id -un)" ]; then
    require_sudo
    sudo chown -R "$SERVICE_USER" "$DATA_DIR"
    sudo chmod -R g+rwX "$DATA_DIR"
    sudo chmod g+s "$DATA_DIR"
fi

# ---------------------------------------------------------------------------
# 6. 生成并安装 systemd 单元
# ---------------------------------------------------------------------------
echo ""
echo "=== 安装 systemd 服务 ==="
echo "运行用户: $SERVICE_USER"
echo "Java:     $JAVA_BIN (version $JAVA_VER)"
echo "JAR:      $JAR_FILE"
echo "数据目录: $DATA_DIR"
echo "端口:     $PORT"

# 安装目录位于 /home、/root 下时 ProtectHome=true 会连 jar 一起屏蔽，服务无法启动；
# 放宽为 read-only（保留"家目录不可写"加固），数据目录写入由 ReadWritePaths 显式白名单放行。
# 与 scripts/package.sh 内嵌安装器保持同一套判定（两安装器逻辑收敛）。
case "$ROOT_DIR" in
    /home/*|/root/*) PROTECT_HOME=read-only ;;
    *)               PROTECT_HOME=true ;;
esac

UNIT_CONTENT=$(cat <<EOF
[Unit]
Description=AnotherViewer Web Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$ROOT_DIR
Environment="ANOTHERVIEWER_DATA_DIR=$DATA_DIR"
ExecStart=$JAVA_BIN -Xms256m -Xmx1024m -XX:+UseG1GC -jar $JAR_FILE --server.port=$PORT
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

# 安全加固
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=$PROTECT_HOME
ReadWritePaths=$DATA_DIR

LimitNOFILE=65536
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF
)

require_sudo
printf '%s\n' "$UNIT_CONTENT" | sudo tee "$UNIT_PATH" > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable "$UNIT_NAME" > /dev/null 2>&1
if systemctl is-active --quiet "$UNIT_NAME"; then
    sudo systemctl restart "$UNIT_NAME"
else
    sudo systemctl start "$UNIT_NAME"
fi
echo "单元已安装: $UNIT_PATH，等待服务就绪..."

# ---------------------------------------------------------------------------
# 7. 健康检查
# ---------------------------------------------------------------------------
READY=0
CODE=000
for _ in $(seq 1 30); do
    CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/v1/auth/status" 2>/dev/null || echo 000)
    # 200/401/403 说明应答的是本应用（认证端点存在）；000=无应答，404=端口被其他服务占用
    case "$CODE" in
        200|401|403) READY=1; break ;;
    esac
    sleep 1
done

if [ "$READY" = 0 ]; then
    echo "错误: 服务未在 30 秒内就绪（/api/v1/auth/status 最后状态码: $CODE）"
    if [ "$CODE" != "000" ]; then
        echo "警告: 端口 $PORT 有其他 HTTP 服务在应答，本服务可能未绑定该端口:"
        sudo ss -tlnp 2>/dev/null | grep ":$PORT " || true
    fi
    echo "最近日志:"
    sudo journalctl -u "$UNIT_NAME" -n 50 --no-pager || true
    exit 1
fi

# ---------------------------------------------------------------------------
# 8. 防火墙
# ---------------------------------------------------------------------------
if systemctl is-active --quiet firewalld; then
    if [ -z "$FIREWALL" ]; then
        ans=$(ask "检测到 firewalld 运行中，放开 $PORT/tcp 允许局域网访问？(y/n)" "y")
        case "$ans" in y|Y) FIREWALL=1 ;; *) FIREWALL=0 ;; esac
    fi
    if [ "$FIREWALL" = 1 ]; then
        if sudo firewall-cmd --quiet --query-port="$PORT/tcp" --permanent; then
            echo "防火墙端口 $PORT/tcp 已开放"
        else
            sudo firewall-cmd --permanent --add-port="$PORT/tcp" > /dev/null
            sudo firewall-cmd --reload > /dev/null
            echo "防火墙已放开 $PORT/tcp"
        fi
    fi
fi

LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo ""
echo "=== 安装完成，服务已就绪 ==="
echo "访问: http://localhost:$PORT${LAN_IP:+  或  http://$LAN_IP:$PORT}"
echo "状态: systemctl status $UNIT_NAME"
echo "日志: journalctl -u $UNIT_NAME -f"
echo "停止: ./stop.sh"
echo "卸载: ./install.sh --uninstall"
