# EhViewer Web App Phase 5: 优化 + 部署 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。

**目标：** Docker 容器化、性能优化、浏览器兼容性、使用文档。

---

## 任务 1：创建 Docker 部署文件

**文件：**
- 创建：`Dockerfile`
- 创建：`docker-compose.yml`
- 创建：`.dockerignore`

- [ ] **步骤 1：创建 Dockerfile**

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 安装字体（图片渲染需要）
RUN apt-get update && apt-get install -y fonts-noto-cjk && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY ehviewer-web/build/libs/ehviewer-web-*.jar app.jar

# 数据和缓存目录
VOLUME ["/app/data", "/app/cache", "/app/downloads"]

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **步骤 2：创建 docker-compose.yml**

```yaml
# docker-compose.yml
version: '3.8'
services:
  ehviewer:
    build: .
    container_name: ehviewer-web
    ports:
      - "8080:8080"
    volumes:
      - ./data:/app/data
      - ./cache:/app/cache
      - ./downloads:/app/downloads
    environment:
      - EHVIEWER_SERVER_PORT=8080
      - EHVIEWER_DOWNLOAD_PATH=/app/downloads
      - EHVIEWER_CACHE_PATH=/app/cache
      - EHVIEWER_CACHE_SIZE_MB=10240
      - EHVIEWER_DOWNLOAD_WORKER_COUNT=3
      - EHVIEWER_DB_URL=jdbc:sqlite:/app/data/ehviewer.db
    restart: unless-stopped
```

- [ ] **步骤 3：创建 .dockerignore**

```
# .dockerignore
build/
.gradle/
.idea/
*.iml
.git
.gitignore
node_modules/
web-frontend/dist/
ehviewer-core/build/
ehviewer-web/build/
app/build/
daogenerator/build/
```

- [ ] **步骤 4：Commit**

```bash
git add Dockerfile docker-compose.yml .dockerignore
git commit -m "feat: add Docker deployment files"
```

---

## 任务 2：创建启动脚本

**文件：**
- 创建：`start.sh`
- 创建：`stop.sh`
- 创建：`build.sh`

- [ ] **步骤 1：创建 build.sh**

```bash
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
```

- [ ] **步骤 2：创建 start.sh**

```bash
#!/bin/bash
# start.sh - 启动服务

set -e

JAR_FILE=$(find ehviewer-web/build/libs -name "ehviewer-web-*.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件，请先运行 ./build.sh"
    exit 1
fi

echo "=== 启动 EhViewer Web ==="
echo "JAR: $JAR_FILE"
echo "端口: 8080"

java -jar "$JAR_FILE" \
    --server.port=8080 \
    --ehviewer.download.path=./data/downloads \
    --ehviewer.cache.path=./data/cache \
    --ehviewer.cache.size-mb=10240
```

- [ ] **步骤 3：创建 stop.sh**

```bash
#!/bin/bash
# stop.sh - 停止服务

PID=$(pgrep -f "ehviewer-web-*.jar")

if [ -n "$PID" ]; then
    echo "=== 停止 EhViewer Web (PID: $PID) ==="
    kill "$PID"
    echo "服务已停止"
else
    echo "服务未运行"
fi
```

- [ ] **步骤 4：设置执行权限**

```bash
chmod +x start.sh stop.sh build.sh
```

- [ ] **步骤 5：Commit**

```bash
git add start.sh stop.sh build.sh
git commit -m "feat: add build and startup scripts"
```

---

## 任务 3：创建使用文档

**文件：**
- 创建：`README.md`
- 创建：`docs/deployment.md`

- [ ] **步骤 1：创建 README.md**

```markdown
# EhViewer Web

将 EhViewer Android 应用转换为局域网内任意设备可通过浏览器访问的 Web App。

## 功能

- 浏览、搜索 E-Hentai 画廊
- 图片阅读器（翻页/滚动/缩放/手势/键盘）
- 下载管理（多级并发、实时进度）
- 收藏管理（10 个收藏夹）
- 评论功能
- 浏览历史
- SMB 备份
- 设置管理

## 快速开始

### Docker 部署（推荐）

```bash
# 构建
./build.sh

# 启动
docker compose up -d

# 访问
open http://localhost:8080
```

### 裸机部署

```bash
# 构建
./build.sh

# 启动
./start.sh

# 访问
open http://localhost:8080
```

### 开发模式

```bash
# 启动后端
./gradlew :ehviewer-web:bootRun

# 启动前端（另一个终端）
cd web-frontend
npm install
npm run dev

# 访问
open http://localhost:3000
```

## 配置

配置文件: `ehviewer-web/src/main/resources/application.yml`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |
| ehviewer.download.path | ./data/downloads | 下载路径 |
| ehviewer.cache.path | ./data/cache | 缓存路径 |
| ehviewer.cache.size-mb | 10240 | 缓存大小 (MB) |
| ehviewer.download.worker-count | 3 | 并发下载线程数 |

## 技术栈

- 后端: Spring Boot 3.x, Kotlin, Java 21
- 前端: Vue 3, Vite, TypeScript, Pinia
- 数据库: SQLite
- 下载: OkHttp 3.14.7
- 解析: jsoup 1.15.4
- SMB: smbj 0.12.0

## 项目结构

```
Ehviewer_CN_SXJ/
├── ehviewer-core/          # 核心业务库 (纯 Java)
├── ehviewer-web/           # Spring Boot Web 应用
├── web-frontend/           # Vue 3 前端
├── app/                    # 原有 Android App
├── Dockerfile              # Docker 镜像
├── docker-compose.yml      # Docker Compose
├── build.sh                # 构建脚本
├── start.sh                # 启动脚本
└── stop.sh                 # 停止脚本
```

## 许可证

MIT License
```

- [ ] **步骤 2：创建 docs/deployment.md**

```markdown
# 部署指南

## 系统要求

- Java 21+
- Node.js 18+ (构建前端)
- 2GB+ RAM
- 10GB+ 磁盘空间

## Docker 部署

### 前置条件

- Docker 20.10+
- Docker Compose 2.0+

### 步骤

1. 克隆仓库
```bash
git clone https://github.com/your-repo/ehviewer-web.git
cd ehviewer-web
```

2. 构建
```bash
./build.sh
```

3. 启动
```bash
docker compose up -d
```

4. 访问
打开浏览器访问 `http://localhost:8080`

### 数据持久化

Docker Compose 会将以下目录挂载到宿主机：

- `./data` - 数据库文件
- `./cache` - 图片缓存
- `./downloads` - 下载文件

### 自定义配置

通过环境变量覆盖默认配置：

```yaml
# docker-compose.yml
services:
  ehviewer:
    environment:
      - EHVIEWER_SERVER_PORT=9090
      - EHVIEWER_CACHE_SIZE_MB=20480
```

## 裸机部署

### 前置条件

- Java 21+
- Node.js 18+ (构建前端)

### 步骤

1. 克隆仓库
```bash
git clone https://github.com/your-repo/ehviewer-web.git
cd ehviewer-web
```

2. 构建
```bash
./build.sh
```

3. 启动
```bash
./start.sh
```

4. 后台运行（可选）
```bash
nohup ./start.sh > ehviewer.log 2>&1 &
```

### systemd 服务（可选）

创建 `/etc/systemd/system/ehviewer.service`:

```ini
[Unit]
Description=EhViewer Web
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/ehviewer-web
ExecStart=/usr/bin/java -jar ehviewer-web/build/libs/ehviewer-web-*.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启用服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable ehviewer
sudo systemctl start ehviewer
```

## 网络存储接入

### CIFS/SMB

```bash
# /etc/fstab
//192.168.6.141/media  /data/ehviewer/downloads  cifs  credentials=/etc/smb-cred,uid=1000,gid=1000,iocharset=utf8  0  0
```

### NFS

```bash
# /etc/fstab
192.168.6.141:/volume1/media  /data/ehviewer/downloads  nfs  defaults,soft,timeo=10,retrans=3  0  0
```

### rclone (云存储)

```bash
rclone mount cloud:/data /data/ehviewer/downloads --vfs-cache-mode full --vfs-cache-max-size 1G
```

## 故障排查

### 端口被占用

```bash
# 查找占用端口的进程
lsof -i :8080

# 杀死进程
kill -9 <PID>
```

### 数据库错误

```bash
# 删除数据库重新创建
rm data/ehviewer.db
# 重启服务
```

### 权限问题

```bash
# 确保目录权限正确
chmod -R 755 data cache downloads
chown -R www-data:www-data data cache downloads
```
```

- [ ] **步骤 3：Commit**

```bash
git add README.md docs/deployment.md
git commit -m "docs: add README and deployment guide"
```

---

## 任务 4：前端构建优化

**文件：**
- 修改：`web-frontend/vite.config.ts`
- 修改：`web-frontend/src/assets/styles/global.css`

- [ ] **步骤 1：优化 Vite 配置**

```typescript
// web-frontend/vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    outDir: '../ehviewer-web/src/main/resources/static',
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
      },
    },
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          axios: ['axios'],
        },
      },
    },
  },
})
```

- [ ] **步骤 2：创建全局样式**

```css
/* web-frontend/src/assets/styles/global.css */
:root {
  --primary-color: #4a90d9;
  --primary-hover: #357abd;
  --danger-color: #e74c3c;
  --success-color: #2ecc71;
  --warning-color: #f39c12;
  --text-color: #333;
  --text-secondary: #666;
  --border-color: #ddd;
  --background-color: #f5f5f5;
  --card-background: white;
  --shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  --radius: 8px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  color: var(--text-color);
  background: var(--background-color);
  line-height: 1.5;
}

a {
  color: var(--primary-color);
  text-decoration: none;
}

a:hover {
  color: var(--primary-hover);
}

button {
  cursor: pointer;
  font-family: inherit;
}

img {
  max-width: 100%;
  height: auto;
}
```

- [ ] **步骤 3：更新 main.ts 引入全局样式**

```typescript
// web-frontend/src/main.ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **步骤 4：Commit**

```bash
git add web-frontend/vite.config.ts \
        web-frontend/src/assets/styles/global.css \
        web-frontend/src/main.ts
git commit -m "feat: optimize Vite build and add global styles"
```

---

## 总结

Phase 5 完成后，系统将具备：
- Docker 容器化部署
- 裸机部署脚本
- 完整的使用文档
- 前端构建优化
- 全局样式统一
