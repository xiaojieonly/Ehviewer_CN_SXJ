# AnotherViewer Cockpit 管理模块 — 独立实现指引

> **读者**：无法接触 AnotherViewer 代码库的 Agent。本文自包含所有必要事实，你只需要：读这篇文章 + 在目标机上执行命令验证。
> **产物**：一个独立的 Cockpit 模块（后续单独 repo 管理），宿主系统为 Linux + systemd（裸机部署形态；Docker 部署不在本文范围）。
> **目标**：管理界面提供 5 项能力——① systemd 服务用户 ② 数据存储地址 ③ 内存大小 ④ 日志监控/搜索/导出 ⑤ 启动/停止/重启服务。

---

## 1. 系统事实（转述自 AnotherViewer 部署文档，可信、无需再读源码）

### 1.1 应用形态

- 后端为 Spring Boot fat JAR：`/opt/anotherviewer/anotherviewer-web.jar`（路径随部署可变，**从 systemd unit 的 ExecStart 读**，见 §3.2）。
- 默认 HTTP 端口 `8080`（`server.port`，可能被 Caddy 反代到 443；模块直连 `http://127.0.0.1:8080`）。
- 依赖系统 Java 21（`/usr/lib/jvm/java-21-openjdk`，unit 里以 `JAVA_HOME` 注入）。

### 1.2 systemd 服务：`anotherviewer-web`

服务单元安装于 `/etc/systemd/system/anotherviewer-web.service`，当前规范内容如下（**以 `systemctl cat anotherviewer-web` 实际输出为准**）：

```ini
[Unit]
Description=AnotherViewer Web Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=anotherviewer
Group=anotherviewer
ExecStart=/usr/bin/java \
    -Xms256m -Xmx1024m \
    -XX:+UseG1GC \
    -jar /opt/anotherviewer/anotherviewer-web.jar \
    --server.port=8080
WorkingDirectory=/opt/anotherviewer
Environment=ANOTHERVIEWER_DATA_DIR=/opt/anotherviewer/data
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk
Restart=on-failure
RestartSec=10
SuccessExitStatus=143
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/anotherviewer/data
PrivateTmp=true
LimitNOFILE=65536
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
```

关键点：

- **服务用户**：`User=anotherviewer`（对应 `Group=anotherviewer`）。
- **数据目录**：`Environment=ANOTHERVIEWER_DATA_DIR`，unit 里为 `/opt/anotherviewer/data`。
- **内存**：JVM 堆由 ExecStart 的 `-Xms256m -Xmx1024m` 决定；另有应用内两级缓存在 §2.2。
- **日志**：stdout/ERR 被 systemd 捕获进 journald，`journalctl -u anotherviewer-web` 可取（§4）。

### 1.3 data-dir 固定结构（迁移/备份的不变量）

`--data-dir` 参数或 `ANOTHERVIEWER_DATA_DIR` 环境变量是**唯一权威数据目录**（默认 `./data`），固定派生：

```
<data-dir>/
├── anotherviewer.db        # SQLite（全部数据）
├── security.key            # 加密密钥（丢失则已存密码不可恢复）
├── downloads/              # 下载内容默认位置
├── cache/                  # 图片缓存
└── backups/                # 备份产物落点
```

- 下载路径 / 缓存路径**可以在应用管理界面单独改**（持久化在 DB 的 `server_config` 表，重启不丢）；其余路径一律由 data-dir 派生。
- `ReadWritePaths=/opt/anotherviewer/data`：unit 只放行该目录可写；改数据目录后**必须同步改 ReadWritePaths**，否则启动即失败（ProtectSystem=strict）。

## 2. 配置键清单

| 键 | 位置 | 默认 | 含义 |
|---|---|---|---|
| `User`/`Group` | systemd unit | `anotherviewer` | 服务运行身份 |
| `ANOTHERVIEWER_DATA_DIR` | unit 环境变量 | `/opt/anotherviewer/data` | 数据目录（唯一权威入口） |
| `-Xmx`/`-Xms` | unit ExecStart | `1024m`/`256m` | JVM 堆 |
| `server.port` | unit ExecStart 参数 / 环境变量 | 8080 | HTTP 端口 |
| `anotherviewer.download.cache-size-mb` | 应用配置 | 10240 | **磁盘**图片缓存上限 MB |
| `anotherviewer.download.cache-memory-mb` | 应用配置 | 64 | **内存**图片缓存上限 MB |

环境变量注入：Spring relaxed binding，`ANOTHERVIEWER_CACHE_SIZE_MB` 等价于 `anotherviewer.download.cache-size-mb`；数据目录除 `--data-dir` 外还可 `ANOTHERVIEWER_DATA_DIR`（命令行等价 `--anotherviewer.data-dir`）。

## 3. 宿主侧读取/写入模型

### 3.1 读取（只读探测，无需应用认证）

```bash
systemctl cat anotherviewer-web            # unit 全文（含 drop-in）
systemctl show anotherviewer-web \
  -p User -p Group -p MainPID -p ActiveState -p SubState \
  -p ExecMainStartTimestamp -p MemoryCurrent -p Environment
systemctl status anotherviewer-web --no-pager
journalctl -u anotherviewer-web --no-pager -n 200    # 最近 200 行
```

### 3.2 写入（一律走 drop-in 覆盖，不碰官方 unit 文件）

修改/新增 `/etc/systemd/system/anotherviewer-web.service.d/override.conf`，然后：

```bash
systemctl daemon-reload
systemctl restart anotherviewer-web    # 视设置项而定，见 §5
```

**重要语义**（很多 Agent 会踩坑）：

- **ExecStart 不叠加**：drop-in 里写 `ExecStart=`（全新空白行）后整行重写，必须**给出完整新 ExecStart**（原命令 + 新 -Xmx 等），其余参数照抄。
- `Environment=` 在 drop-in 中同样整组覆盖原 unit 的 `Environment=` 行；多值可在 drop-in 内写多行 `Environment=` 追加。
- `ReadWritePaths=` 从 unit 复制带来；改数据目录时它必填为新目录。
- `User=anotherviewer` 更改后需确保新用户对 `data-dir` 及 jar 目录有读写权（`chown -R newuser:newgroup <data-dir>`，`chmod 750`）。

## 4. 应用诊断 API（HTTP，独立于 systemd）

以下端点**无需应用登录**（Spring Security permitAll）：

```
GET http://127.0.0.1:8080/api/v1/health
GET http://127.0.0.1:8080/api/v1/metrics
GET http://127.0.0.1:8080/api/v1/metrics/dashboard
```

- `/api/v1/health`：`{status: "UP|DEGRADED|DOWN", components:{database,diskCache,galleryApi,waifu2x}, version, uptimeMs}`。
- `/api/v1/metrics/dashboard`：预聚合面板数据，字段含 `summary.status/version/uptime`、`cache.{memoryUsedBytes,memoryMaxBytes,memoryUsagePercent,diskUsedBytes,diskMaxBytes,diskUsagePercent,hitRatio}`、`downloads.{active,failedTotal,activeTasks}`、`processing.{queueSize,activeCount,processorAvailable}`、`recentErrors`（最近 20 条 ERROR 事件，内存环形缓冲，重启清空）等。
- 应用其它 `/api/**`（如 `/api/v1/settings` 改下载/缓存路径）**均需 Bearer token**：先 `POST /api/v1/auth/login {username,password}` 取 `token`。本模块**不建议**管理应用内配置（§9 边界），如要在界面显示缓存大小，用 `/api/v1/metrics/dashboard` 只读即可。
- 服务停机时这些端点不可达：这是 UI 里明确"服务状态"的依据之一（与 systemd ActiveState 双源比对）。

## 5. 五大功能的实现指引

### 5.1 systemd 服务用户（功能①）

- 读：`systemctl show -p User -p Group`；附 `id <user>` 校验存在性与组。
- 改：drop-in 写 `User=`/`Group=` → `daemon-reload` → 弹出确认：会以新身份运行，需 `chown -R 新用户 group dir` 且 `ReadWritePaths` 保持 data-dir。
- 校验：`systemctl show -p User` 重读 + `cat /proc/<MainPID>/status | grep Uid` 核对真实 uid（防 drop-in 未生效）。

### 5.2 数据存储地址（功能②）

- 读：三种来源交叉核对——① `systemctl show -p Environment`（ANOTHERVIEWER_DATA_DIR）② `systemctl cat` 的 ExecStart 里 `--data-dir=` ③ `/api/v1/health` 的 `components.database.details.path`（落库路径，唯一权威事实）。显示时给出"权威=数据库路径"。
- 改：drop-in 设 `Environment=ANOTHERVIEWER_DATA_DIR=新路径` + `ReadWritePaths=新路径` → `daemon-reload` → 停止服务 → 询问是否迁移现有数据（推荐 UI 后端执行 `rsync -a 旧/ 新/` 并保持 `/downloads /cache /backups` 子结构）→ 确认权限 → start。
- **警告 UI**：直接换目录不清库需要手动拷贝 data-dir 的固定结构；security.key 必须一并迁移，否则已存密码/加密数据不可恢复。

### 5.3 内存大小（功能③）

- **JVM 堆**（本模块唯一强职责）：从 `systemctl cat` 的 `-Xms/-Xmx` 正则提取当前值；UI 提供最大值预设（如 512m/1g/2g/4g 或自定义 ≥256m）；drop-in 重写完整 ExecStart 后 `daemon-reload + restart`。
- **应用缓存层**（只读展示）：`/api/v1/metrics/dashboard` 给 `cache.memoryMaxBytes`（64MB 上限）与 `diskMaxBytes`，界面可提示"应用内缓存大小，非堆配置"。
- **显示**：`systemctl show -p MemoryCurrent` 实时 RSS + `MemoryMax`（如未设则 `systemd-analyze cat-config 无限制`）；JVM 实际堆峰值可提示用户用 `jcmd <pid> GC.heap_info` 自查（不强制实现）。
- 约束：Xmx 不得超过宿主 RAM；给出 `free -h` 上下文。

### 5.4 日志监控、搜索和导出（功能④）

数据源唯一：**journald**（服务 stdout 为 JSON Lines）。

- 监控：实时尾部。实现方式：`cockpit.spawn(["journalctl", "-u", "anotherviewer-web", "-f", "-o", "json"])` 持续读取，每行解析为一个 JSON 对象（logstash-logback-encoder 输出，应用也支持 `anotherviewer.logging.json-enabled=false` 的纯文本模式——解析失败时降级为原文行显示）。
- 结构化字段（JSON 模式时可信）：`@timestamp`(UTC ISO)、`level`、`logger`、`message`、`traceId`、`thread`、`event`。常见 `event` 值：`image.cache.hit/miss`、`image.download`、`image.process`、`download.task`、`sync.operation`。
- 搜索：
  - 简单模式：由前端过滤已加载行（仅覆盖缓冲内）。
  - 完整模式：`journalctl -u anotherviewer-web --since <ISO> --until <ISO> -o json | jq 'select(.event=="download.task" or (.level|test("ERROR|WARN")))'`（无 jq 时退化为逐行文本匹配）。
  - 支持时间窗（`--since/-S`、`--until/-U`，如 "1h ago"、"2026-08-30T12:00:00"）、`-p <err|warning|info|debug>`、`--no-pager -n N` 控制条数。
- 导出：把查询结果（JSON 行或预处理文本）在浏览器端生成 `Blob` 下载为 `.log`/`.jsonl` 文件，**文件名带时间戳**（如 `anotherviewer-web-20260830134500.jsonl`）。
- 注意：journald 默认轮转策略（`/etc/systemd/journald.conf` SystemMaxUse）；如需历史可提示用户 `journalctl --vacuum-time`。**不需要也不应**让模块去读 `/var/log/` 或应用自己的文件。

### 5.5 快速启动/停止/重启（功能⑤）

- 实现：定位/调 `systemctl start|stop|restart anotherviewer-web`，操作期间禁用按钮+进度态。
- 状态栏常量轮询（≤5s）：`systemctl is-active` + `systemctl show -p ActiveState -p SubState -p MainPID` + `/api/v1/health` 三源合并状态：`running / starting / degraded / stopped / failed / restarting`。
- 停止用 `stop`（Type=simple，`TimeoutStopSec=30`，应用收 SIGTERM 优雅退出并 `SuccessExitStatus=143`）；**避免 `kill -9`**，仅在异常场景应提供 "强制终止" 二次确认按钮（kill MainPID）。
- 操作后给出结果反馈与最近 3 行日志（新状态日志），失败时把 `journalctl -u anotherviewer-web -n 200` 尾部错误展示给用户。

## 6. Cockpit 模块工程骨架

Cockpit 模块 = 静态包 + manifest：

```
anotherviewer-admin/
├── package.json          # name, version, description
├── cockpit-manifest.json # plugin 声明
└── src/
    ├── index.html        # 单页应用容器（自己实现 UI，不用 framework 也可）
    ├── app.js            # 入口
    ├── api.js            # 应用 REST 封装（fetch 到 :8080）
    └── systemd.js        # systemd/journalctl 封装
```

要点：

- manifest 声明插件与"应用程序"标识；浏览器加载的是静态 HTML/JS，**权限在服务端委托**：Cockpit 会话以登录用户（管理员=root，或拥有 sudo 的组）身份运行，`cockpit.spawn()` 即以此身份执行命令。
- 命令执行统一走 `cockpit.spawn([...])`（返回 promise，含 stdout/stderr/exit）；长流式（日志 tail）用 spawn 的 `stream` 回调持续喂行。
- 连接 `http://127.0.0.1:8080` 做应用 API 时直接用 `fetch`，注意 CORS：应用未配置 CORS 的情况下，**优先聚合到 Cockpit 侧执行**（`cockpit.spawn(["curl", "-s", ...])`），或者要求 Cockpit 以 `localhost` 同源容器访问（推荐前者，简单可靠）。
- UIConventions：卡片式面板、`cockpit-*` Web Components（`cockpit-panel`、`cockpit-table`、`cockpit-alert`）；中文文案，跟随 CONTEXT.md 术语：**data-dir / 数据目录**、服务用户、内存（-Xmx）、日志。
- 打包：`npm run build` 产出 dist/，部署即 `sudo cp -r dist/anotherviewer* /usr/share/cockpit/` 后刷新 Cockpit 侧栏出现入口。

## 7. 安全与规则

1. **最小权限**：模块只做展示/启停/写 drop-in；所有写操作需在 UI 内二次确认（填写确认词或显示将要执行的 diff）。
2. 变更 systemd unit 前展示 `diff`：`systemctl cat`（当前）vs 生成的 override.conf。
3. 数据目录改动必须先提示"security.key 必须一起迁"。
4. 若 `User` 被改过，重启前检查 `core` 目录（若配置）及 data-dir 属主。
5. 缓存 `systemctl` 查询结果 ≤5s 防止频繁调用；应用 API 失败（服务停机）时降级为 systemd 单一状态源再加"服务无响应"提示。

## 8. 验收清单（在全新/现有部署机上执行）

```bash
# A. 状态一致性
systemctl is-active anotherviewer-web
curl -s http://127.0.0.1:8080/api/v1/health | jq .status

# B. 配置读取
systemctl show anotherviewer-web -p User -p Group -p Environment -p MainPID
systemctl cat anotherviewer-web | grep -E 'Xmx|data-dir|ANOTHERVIEWER_DATA_DIR'

# C. 日志
journalctl -u anotherviewer-web -n 5 --output=json | jq -r '.[].message'
journalctl -u anotherviewer-web --since "5 min ago" -p err --no-pager | head

# D. 写回（改动后）
systemctl restart anotherviewer-web && sleep 3 && curl -s http://127.0.0.1:8080/api/v1/health | jq .status
```

以上 C/D 对应模块的四个核心路径；模块功能通过即验收通过。

## 9. 明确边界（非目标）

- 不管理应用内部设置（下载路径、缓存路径、登录态、SMB、代理、备份）——它们已有 WebUI 管理页；管理界面里只做**只读展示**（dashboard metrics）与 systemd 层操作。
- 不管 Docker/compose 部署形态（除非后续新增检测）。
- 不管 Caddy/HTTPS 证书。
- 不读取/解析 `anotherviewer-web.log` 等应用自己的文件——应用 stdout 进 journald，这是唯一日志源。
- 不做在线编辑 JVM 参数（需要重启生效，UI 必须明示）。

---

*附：本文件基于 AnotherViewer 仓库 `docs/deployment.md`、`deploy/anotherviewer-web.service`、`contracts/observability.md`、`anotherviewer-web/src/main/resources/application.yml` 编写（rev. 2026-08-30）。事实若有出入以目标机 `systemctl cat` 为准。*
