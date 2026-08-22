#!/bin/bash
# build.sh - 构建项目

set -e

# --configure-on-demand：web 构建只配置所需项目，避免根项目 AGP 连带配置 :app
# 而要求 Android SDK（服务器只跑 web 时无需安装 Android 依赖）
echo "=== 构建 anotherviewer-core ==="
./gradlew --configure-on-demand :anotherviewer-core:build -x test

echo "=== 构建前端（必须先于 bootJar：前端 dist 打进 anotherviewer-web jar）==="
cd web-frontend
npm install
npm run build
cd ..

echo "=== dist 新鲜度门（防止把陈旧前端打进 jar）==="
# 门放在前端构建之后：构建前"源码比 dist 新"是常态（旧门放在构建前，恰好拦掉
# 本次马上要执行的重建，形成自败）；构建后源码仍比 dist 新只可能是 npm run build
# 未真正产出（静默失败），此时才需要失败拦截。
STATIC=anotherviewer-web/src/main/resources/static/index.html
if [ ! -f "$STATIC" ]; then
  echo "ERROR: 前端构建后未发现 $STATIC，请检查 web-frontend 构建输出路径配置" >&2
  exit 1
fi
if find web-frontend/src web-frontend/package.json -newer "$STATIC" 2>/dev/null | grep -q .; then
  echo "ERROR: 前端构建后源码仍比 resources/static 新，npm run build 可能未生效，请检查上方构建日志" >&2
  exit 1
fi

echo "=== 构建 anotherviewer-web ==="
./gradlew --configure-on-demand :anotherviewer-web:bootJar

echo "=== 构建完成 ==="
echo "JAR 文件: anotherviewer-web/build/libs/anotherviewer-web-*.jar"
