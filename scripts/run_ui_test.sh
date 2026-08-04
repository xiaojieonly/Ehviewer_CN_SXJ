#!/bin/bash
# EhViewer iOS27 UI 自动化测试
#
# 流程:构建 app+test APK → 安装 → 按主题矩阵运行插桩测试 → 拉取截图与结果。
# 测试以插桩身份注入触摸事件,绕过 MIUI 对 shell input 的 INJECT_EVENTS 限制;
# 但仍需在开发者选项开启「USB调试(安全设置)」。
#
# 用法: scripts/run_ui_test.sh [device_serial] [theme]
#   device_serial  默认 6ffebe3eda93
#   theme          all(默认) | light(0) | dark(1) | black(2)
#
# 结果: uitest-results/<theme>/*.png 截图;终端输出 am instrument 结果。

set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${1:-6ffebe3eda93}"
THEME="${2:-all}"

PKG="com.xjs.ehviewer.debug"
PREFS="/data/data/$PKG/shared_prefs/${PKG}_preferences.xml"
APP_APK="app/build/intermediates/apk/appRelease/debug/app-appRelease-debug.apk"
TEST_APK="app/build/intermediates/apk/androidTest/appRelease/debug/app-appRelease-debug-androidTest.apk"
OUT_DIR="uitest-results"

cd "$(dirname "$0")/.."

# ---- 主题 -> shared_prefs 写入(0=light 1=dark 2=black,空串=删除键恢复默认浅色) ----
set_theme() {
    local theme="$1"
    "$ADB" -s "$SERIAL" shell am force-stop "$PKG"
    "$ADB" -s "$SERIAL" shell run-as "$PKG" cat "$PREFS" > /tmp/eh_uitest_prefs.xml
    python3 - "$theme" <<'EOF'
import re, sys
p = '/tmp/eh_uitest_prefs.xml'
s = open(p, encoding='utf-8').read()
s = re.sub(r'\s*<string name="theme">\d</string>', '', s)
if sys.argv[1] != '':
    s = s.replace('</map>', '    <string name="theme">%s</string>\n</map>' % sys.argv[1])
open(p, 'w', encoding='utf-8').write(s)
EOF
    "$ADB" -s "$SERIAL" shell "run-as $PKG sh -c 'cat > $PREFS'" < /tmp/eh_uitest_prefs.xml
}

run_suite() {
    local tag="$1"
    echo "===== [$tag] 运行插桩测试 ====="
    "$ADB" -s "$SERIAL" shell am force-stop "$PKG"
    "$ADB" -s "$SERIAL" shell run-as "$PKG" rm -rf files/uitest 2>/dev/null || true

    local instr
    instr=$("$ADB" -s "$SERIAL" shell pm list instrumentation | grep "$PKG" | head -1 \
        | sed 's/^instrumentation://; s/ (.*$//')
    if [ -z "$instr" ]; then
        echo "ERROR: 未找到插桩组件,test APK 是否已安装?" >&2
        exit 1
    fi

    local output status
    # -e class: 避免 JUnit3 runner 全量扫描 dex(大应用会卡死被 ANR 看门狗杀掉)
    output=$("$ADB" -s "$SERIAL" shell am instrument -w -r \
        -e class com.hippo.ehviewer.UiFlowTest,com.hippo.ehviewer.ModernUiTest "$instr" 2>&1 || true)
    status=0
    echo "$output" | grep -q '^OK ' || status=1
    echo "$output" | tail -30

    mkdir -p "$OUT_DIR/$tag"
    for f in $("$ADB" -s "$SERIAL" shell run-as "$PKG" ls files/uitest 2>/dev/null); do
        "$ADB" -s "$SERIAL" shell run-as "$PKG" cat "files/uitest/$f" > "$OUT_DIR/$tag/$f"
    done
    echo "===== [$tag] 截图已保存到 $OUT_DIR/$tag ====="
    return $status
}

# ---- 构建与安装 ----
echo "===== 构建 app 与 test APK ====="
./gradlew :app:assembleAppReleaseDebug :app:assembleAppReleaseDebugAndroidTest \
    -Pandroid.injected.build.abi=arm64-v8a

"$ADB" -s "$SERIAL" install -r -t "$APP_APK"
"$ADB" -s "$SERIAL" install -r -t "$TEST_APK"

# ---- 主题矩阵 ----
overall=0
case "$THEME" in
    all)    themes="0:light 1:dark 2:black" ;;
    light|0) themes="0:light" ;;
    dark|1)  themes="1:dark" ;;
    black|2) themes="2:black" ;;
    *) echo "未知 theme: $THEME" >&2; exit 1 ;;
esac

for pair in $themes; do
    num="${pair%%:*}"
    name="${pair##*:}"
    set_theme "$num"
    run_suite "$name" || overall=1
done

# ---- 恢复默认浅色 ----
set_theme ""
"$ADB" -s "$SERIAL" shell am force-stop "$PKG"

echo
if [ "$overall" = "0" ]; then
    echo "===== 全部通过 ====="
else
    echo "===== 存在失败,详见上方输出与 $OUT_DIR 截图 ====="
fi
exit $overall
