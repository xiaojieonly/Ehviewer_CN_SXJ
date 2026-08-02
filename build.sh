#!/bin/bash
# build.sh - 构建项目

set -e

echo "=== 构建 anotherviewer-core ==="
./gradlew :anotherviewer-core:build -x test

echo "=== 构建 anotherviewer-web ==="
./gradlew :anotherviewer-web:bootJar

echo "=== 构建前端 ==="
cd web-frontend
npm install
npm run build
cd ..

echo "=== 构建完成 ==="
echo "JAR 文件: anotherviewer-web/build/libs/anotherviewer-web-*.jar"
