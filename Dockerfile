# Dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 安装字体（图片渲染需要）
RUN apt-get update && apt-get install -y fonts-noto-cjk && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY anotherviewer-web/build/libs/anotherviewer-web-*.jar app.jar

# 数据和缓存目录
VOLUME ["/app/data", "/app/cache", "/app/downloads"]

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
