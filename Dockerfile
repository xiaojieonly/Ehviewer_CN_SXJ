# Dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 安装字体（图片渲染需要）
RUN apt-get update && apt-get install -y fonts-noto-cjk && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY anotherviewer-web/build/libs/anotherviewer-web-*.jar app.jar

# 非 root 运行（M-12）：创建 UID 1000 的应用用户，并预置数据目录属主。
# setpriv 来自 util-linux（Ubuntu jammy 基础镜像必备包）。
RUN groupadd --gid 1000 appuser \
    && useradd --uid 1000 --gid appuser --shell /usr/sbin/nologin --no-create-home appuser \
    && mkdir -p /app/data /app/cache /app/downloads \
    && chown -R appuser:appuser /app/data /app/cache /app/downloads

# 数据和缓存目录
VOLUME ["/app/data", "/app/cache", "/app/downloads"]

EXPOSE 8080

# ENTRYPOINT 短暂以 root 修正挂载卷属主（bind mount 的宿主机目录属主不定、
# 旧 root 镜像的 named volume 内容也归 root），随后经 setpriv 降权为
# appuser(UID 1000) 执行 java —— 应用进程始终是 non-root。
# 仅当顶层目录属主非 appuser 时才递归 chown，避免每次启动 O(downloads) 扫描。
ENTRYPOINT ["sh", "-c", "if [ \"$(stat -c '%u' /app/data)\" != \"1000\" ] || [ \"$(stat -c '%u' /app/cache)\" != \"1000\" ] || [ \"$(stat -c '%u' /app/downloads)\" != \"1000\" ]; then chown -R appuser:appuser /app/data /app/cache /app/downloads; fi; exec setpriv --reuid=appuser --regid=appuser --init-groups java -jar app.jar"]
