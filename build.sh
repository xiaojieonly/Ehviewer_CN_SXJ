#!/bin/bash
# build.sh - 构建项目

set -e

echo "=== 构建 ehviewer-core ==="
./gradlew :ehviewer-core:build -x test

echo "=== 构建 ehviewer-web ==="
./gradlew :ehviewer-web:bootJar

echo "=== 构建前端 ==="
cd web-frontend
npm install
npm run build
cd ..

echo "=== 构建完成 ==="
echo "JAR 文件: ehviewer-web/build/libs/ehviewer-web-*.jar"
