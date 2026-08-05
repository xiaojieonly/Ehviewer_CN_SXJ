#!/bin/bash
# build.sh - 构建项目

set -e

# --configure-on-demand：web 构建只配置所需项目，避免根项目 AGP 连带配置 :app
# 而要求 Android SDK（服务器只跑 web 时无需安装 Android 依赖）
echo "=== 构建 anotherviewer-core ==="
./gradlew --configure-on-demand :anotherviewer-core:build -x test

echo "=== dist 新鲜度门（防止把陈旧前端打进 jar）==="
if [ -f anotherviewer-web/src/main/resources/static/index.html ] && find web-frontend/src web-frontend/package.json -newer anotherviewer-web/src/main/resources/static/index.html 2>/dev/null | grep -q .; then
  echo "ERROR: 前端源码比 resources/static 新，请先运行 'cd web-frontend && npm run build'" >&2
  exit 1
fi

echo "=== 构建前端（必须先于 bootJar：前端 dist 打进 anotherviewer-web jar）==="
cd web-frontend
npm install
npm run build
cd ..

echo "=== 构建 anotherviewer-web ==="
./gradlew --configure-on-demand :anotherviewer-web:bootJar

echo "=== 构建完成 ==="
echo "JAR 文件: anotherviewer-web/build/libs/anotherviewer-web-*.jar"
