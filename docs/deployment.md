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
Description=AnotherViewer Web
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
