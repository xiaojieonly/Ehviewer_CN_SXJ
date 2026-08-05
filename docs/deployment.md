# 部署指南

## 系统要求

- Java 21+
- Node.js 18+ (构建前端)
- 2GB+ RAM
- 10GB+ 磁盘空间

## 分发形态

官方发布产物为 **zip 包**（GitHub Releases）：`anotherviewer-<version>-<os>-<arch>.zip`，由 `scripts/package.sh` 生成。结构：

```
lib/app.jar        # 后端可执行 jar（含前端静态资源）
bin/start.sh       # 启动（脚本目录推导，--data-dir 透传）
bin/stop.sh
data/              # 数据目录模板（固定结构，见下）
README.txt
```

安装即解压；**依赖系统 Java 21**（不含 JRE）。发行版包管理器（deb/rpm）的预适配骨架在 `packaging/`（见 `packaging/README.md`）。

### 数据目录（data-dir）

服务器唯一的权威数据目录，由 `--data-dir` 参数或 `ANOTHERVIEWER_DATA_DIR` 环境变量指定（默认 `./data`）。固定结构：

```
<data-dir>/
├── anotherviewer.db        # SQLite（同步实体、用户配置 server_config 等全部数据）
├── security.key       # token/密码加密密钥
├── downloads/         # 下载内容默认位置（可用管理界面改到其他路径，持久化）
├── cache/             # 图片缓存
└── backups/           # 备份产物落点
```

下载路径与缓存路径可在管理界面单独设置（持久化于 `server_config` 表，重启不丢）；其余路径一律由 data-dir 派生。迁移/备份基于此固定结构，**与具体路径无关**。

## 快速启动（zip）

```bash
unzip anotherviewer-<version>-*.zip -d ~/anotherviewer
cd ~/anotherviewer
./bin/start.sh                 # 默认 data-dir = ./data
./bin/start.sh --data-dir=/srv/anotherviewer   # 自定义数据目录（docker 挂载卷的等价物）
./bin/stop.sh
```

systemd 服务（可选）：`packaging/systemd/anotherviewer.service.tpl`（`ExecStart=... --data-dir=/var/lib/anotherviewer`）。

## Docker 部署

### 前置条件

- Docker 20.10+
- Docker Compose 2.0+

### 步骤

1. 克隆仓库
```bash
git clone https://github.com/PegionFish/AnotherViewer.git
cd AnotherViewer
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
  anotherviewer:
    environment:
      - ANOTHERVIEWER_SERVER_PORT=9090
      - ANOTHERVIEWER_CACHE_SIZE_MB=20480
```

## 裸机部署

### 前置条件

- Java 21+
- Node.js 18+ (构建前端)

### 步骤

1. 克隆仓库
```bash
git clone https://github.com/PegionFish/AnotherViewer.git
cd AnotherViewer
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
nohup ./start.sh > anotherviewer.log 2>&1 &
```

### systemd 服务（可选）

创建 `/etc/systemd/system/anotherviewer.service`:

```ini
[Unit]
Description=AnotherViewer Web
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/anotherviewer-web
ExecStart=/usr/bin/java -jar anotherviewer-web/build/libs/anotherviewer-web-*.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启用服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable anotherviewer
sudo systemctl start anotherviewer
```

## 网络存储接入

### CIFS/SMB

```bash
# /etc/fstab
//192.168.6.141/media  /data/anotherviewer/downloads  cifs  credentials=/etc/smb-cred,uid=1000,gid=1000,iocharset=utf8  0  0
```

### NFS

```bash
# /etc/fstab
192.168.6.141:/volume1/media  /data/anotherviewer/downloads  nfs  defaults,soft,timeo=10,retrans=3  0  0
```

### rclone (云存储)

```bash
rclone mount cloud:/data /data/anotherviewer/downloads --vfs-cache-mode full --vfs-cache-max-size 1G
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
rm data/anotherviewer.db
# 重启服务
```

### 权限问题

```bash
# 确保目录权限正确
chmod -R 755 data cache downloads
chown -R www-data:www-data data cache downloads
```

## 备份 / 还原 / 迁移

备份与还原在管理界面「备份」页操作（`GET /api/v1/backup/export`、`POST /api/v1/backup/restore`），产物格式见 `contracts/backup-format.md`。

- **备份**：固定结构打包（db + security.key + server_config；下载内容默认排除、可选包含）。分片是独立 7z 文件 + manifest，可单独拷到 NAS/U 盘异地备份。还原前旧文件保留 `.bak`，需确认词 `RESTORE` + 重启生效
- **迁移（换机/换目录）**：三种等价途径——
  1. 备份（元数据，≤50MB）→ 新机器 WebUI 还原
  2. 含下载内容的大备份（GB 级）→ 手动解包分片/直接拷贝 data-dir（**WebUI 上传限 50MB**，面向元数据）
  3. 直接拷贝整个 data-dir（结构固定，目标路径可以不同）
- **App 包名迁移（com.xjs.anotherviewer → com.pf.anotherviewer）**：旧包名 app 覆盖安装 legacy 包（`-PapplicationId=com.xjs.anotherviewer`）→ 手动同步推数据到服务器 → 新包名 app 配对拉全量；下载文件留在原存储位置，新包名 app 重新授权 SAF 目录即复用

### 从原版 EhViewer 迁移

原版 EhViewer 的本地数据可经「导出数据」得到 `.db` 文件，导入本 App 后经同步推上服务器，完成跨 app 迁移。

**迁移路径**：

1. 旧设备：原版 EhViewer「设置 → 高级 → 导出数据」，得到 `yyyy-MM-dd-HH-mm-ss-SSS.db`（导出到外置存储）
2. 拷贝该 `.db` 到新机
3. 用 **legacy 包**（`-PapplicationId=com.xjs.anotherviewer`，见 应用标识 词条）覆盖安装
4. App「设置 → 高级 → 导入数据」，选择该 `.db`
5. 手动触发同步，把数据推上 WebUI 服务器
6. 新包名 app（`com.pf.anotherviewer`）配对后拉全量

**导入语义（importDB）**：

- importDB 自动把 v7 库升级到 v8：补齐同步元数据列，进度字段落 0，下载记录 STATE 保留
- 迁移的数据 = 下载记录、下载目录名台账、历史、本地收藏、书签、过滤、快速搜索、下载标签
- 偏好不迁移；黑名单 / 画廊标签不在导出范围内

**登录授权说明**：

- 导出的 `.db` 文件**不含登录授权（cookies）**；登录态（`ipb_member_id` / `ipb_pass_hash` / `igneous` 等）保存在 app 内部数据目录的 `okhttp3-cookie.db` 与 shared_prefs
- 覆盖安装 legacy 包时，登录态随应用数据目录原样保留，**无需额外操作**
- 导入 `.db` 只迁业务表，不影响已保留的登录态
