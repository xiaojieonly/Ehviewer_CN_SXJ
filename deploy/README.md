# EhViewer Web — 生产部署指南

## 架构概览

```
┌─────────────┐     HTTPS      ┌───────────┐    HTTP     ┌──────────────────┐
│   Browser    │ ────────────→ │   Caddy    │ ─────────→ │  Spring Boot     │
│   (PWA)      │    :443       │  (反代)    │   :8080    │  (ehviewer-web)  │
└─────────────┘               └───────────┘            └──────────────────┘
                                                            │
                                                     ┌──────┴──────┐
                                                     │   SQLite    │
                                                     │   + 文件    │
                                                     └─────────────┘
```

## 1. 构建

```bash
# 前端构建（输出到 ehviewer-web/src/main/resources/static/）
cd web-frontend && npm ci && npm run build && cd ..

# 后端构建（含前端静态资源的 fat JAR）
./gradlew :ehviewer-web:bootJar -x test

# 产物位置
ls ehviewer-web/build/libs/ehviewer-web-*.jar
```

## 2. 部署方式

### 方式 A：Docker（推荐）

```bash
docker compose up -d
```

数据持久化在 `./data/`、`./cache/`、`./downloads/` 目录。

### 方式 B：systemd + Caddy（裸机）

```bash
# 创建服务用户
sudo useradd -r -s /bin/false -d /opt/ehviewer ehviewer

# 部署文件
sudo mkdir -p /opt/ehviewer/data/{db,downloads,cache,enhanced}
sudo cp ehviewer-web/build/libs/ehviewer-web-*.jar /opt/ehviewer/ehviewer-web.jar
sudo chown -R ehviewer:ehviewer /opt/ehviewer

# 安装 systemd 服务
sudo cp deploy/ehviewer-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ehviewer-web

# 安装 Caddy 反代（自动 HTTPS）
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
# 编辑 /etc/caddy/Caddyfile，替换 ehviewer.example.com 为你的域名
sudo systemctl reload caddy
```

## 3. HTTPS 与 PWA

PWA（Service Worker、离线阅读）**要求安全上下文**：
- `https://` 域名（Caddy 自动获取 Let's Encrypt 证书）
- 或 `http://localhost`（开发环境）

局域网无域名访问时，PWA 功能不可用，但基本阅读功能正常。

## 4. 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8080 | HTTP 端口 |
| `ehviewer.download.path` | ./data/downloads | 下载存储路径 |
| `ehviewer.download.cache-path` | ./data/cache | 图片缓存路径 |
| `ehviewer.security.encryption-key-path` | ./data/security.key | 加密密钥文件 |
| `ehviewer.processing.concurrency` | 1 | 图片处理并发数 |
| `EHVIEWER_DATA_DIR` | ./data/db | SQLite 数据库目录 |

## 5. 监控

- 健康检查：`GET /api/v1/health`
- 指标：`GET /api/v1/metrics`
- 日志：`journalctl -u ehviewer-web -f`
- Caddy 访问日志：`/var/log/caddy/ehviewer-access.log`

## 6. 备份

需要备份的数据：
- `/opt/ehviewer/data/db/` — SQLite 数据库（收藏、历史、设置）
- `/opt/ehviewer/data/downloads/` — 下载的画廊
- `/opt/ehviewer/data/security.key` — 加密密钥（丢失则已存密码不可恢复）
