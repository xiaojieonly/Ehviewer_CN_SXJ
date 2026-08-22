# AnotherViewer Web — 生产部署指南

## 架构概览

```
┌─────────────┐     HTTPS      ┌───────────┐    HTTP     ┌──────────────────┐
│   Browser    │ ────────────→ │   Caddy    │ ─────────→ │  Spring Boot     │
│   (PWA)      │    :443       │  (反代)    │   :8080    │  (anotherviewer-web)  │
└─────────────┘               └───────────┘            └──────────────────┘
                                                            │
                                                     ┌──────┴──────┐
                                                     │   SQLite    │
                                                     │   + 文件    │
                                                     └─────────────┘
```

## 1. 构建

```bash
# 前端构建（输出到 anotherviewer-web/src/main/resources/static/）
cd web-frontend && npm ci && npm run build && cd ..

# 后端构建（含前端静态资源的 fat JAR）
./gradlew :anotherviewer-web:bootJar -x test

# 产物位置
ls anotherviewer-web/build/libs/anotherviewer-web-*.jar
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
sudo useradd -r -s /bin/false -d /opt/anotherviewer anotherviewer

# 部署文件（data-dir 固定结构：db/security.key 落在 data/ 根，子目录仅 downloads/cache/backups）
sudo mkdir -p /opt/anotherviewer/data/{downloads,cache,backups}
sudo cp anotherviewer-web/build/libs/anotherviewer-web-*.jar /opt/anotherviewer/anotherviewer-web.jar
sudo chown -R anotherviewer:anotherviewer /opt/anotherviewer

# 安装 systemd 服务
sudo cp deploy/anotherviewer-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now anotherviewer-web

# 安装 Caddy 反代（自动 HTTPS）
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
# 编辑 /etc/caddy/Caddyfile，替换 anotherviewer.example.com 为你的域名
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
| `anotherviewer.download.path` | `<data-dir>/downloads` | 下载存储路径（默认由 data-dir 派生） |
| `anotherviewer.download.cache-path` | `<data-dir>/cache` | 图片缓存路径（默认由 data-dir 派生） |
| `anotherviewer.security.encryption-key-path` | `<data-dir>/security.key` | 加密密钥文件（默认由 data-dir 派生） |
| `anotherviewer.processing.concurrency` | 1 | 图片处理并发数 |
| `ANOTHERVIEWER_DATA_DIR` | `./data` | 权威数据目录：db / security.key / downloads/ / cache/ / backups/ 均由它派生 |

## 5. 监控

- 健康检查：`GET /api/v1/health`
- 指标：`GET /api/v1/metrics`
- 日志：`journalctl -u anotherviewer-web -f`
- Caddy 访问日志：`/var/log/caddy/anotherviewer-access.log`

## 6. 备份

需要备份的数据（均在 data-dir 下，结构固定、与路径无关）：
- `/opt/anotherviewer/data/anotherviewer.db` — SQLite 数据库（收藏、历史、设置）
- `/opt/anotherviewer/data/security.key` — 加密密钥（丢失则已存密码不可恢复）
- `/opt/anotherviewer/data/downloads/` — 下载的画廊
- `/opt/anotherviewer/data/backups/` — 备份产物落点
