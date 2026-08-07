# 0005: 数据层迁移到 PostgreSQL（多写者并发）

将 WebUI 服务器的数据层从单文件 SQLite 迁移到 PostgreSQL，解除 SQLite 文件级单写者锁对并发写（保存配置、多设备同步、后台任务）的长期约束；备份/迁移契约（ADR-0002）随之重设计。

## 背景：SQLite 单写者锁的实际故障

2026-08-07 实测故障（WebUI 保存配置失败，`PUT /api/v1/preferences` 500）：

- 堆栈：`CannotAcquireLockException [SQLITE_BUSY] The database file is locked [update user_preference ... where id=?]`，起于 `UserPreferenceService.update` 的 `repo.save`
- 根因：`EhImportService.runImport`（EhViewer 备份导入）用单个 `@Transactional` 包裹整个多表导入（数万行逐行 save，分钟级）。SQLite 是**文件级单写者**——导入期间独占写锁，任何并发写（保存配置、sync push）等满 `busy_timeout=30000` 后只能 `SQLITE_BUSY` 失败
- 佐证：锁失败时间（07:50–07:56）与 IMPORT 任务完成时间（07:59:13）完全重叠；GET 正常、仅写失败（WAL 读不阻塞，符合单写者特征）

临时修复（已上线）：`runImport` 拆成每 500 行一个短事务（`forEachRow` + `TransactionTemplate`，REQUIRED 传播），写锁持有从分钟级降到毫秒级。此修复保留，作为迁移完成前的止血。

## 决策：迁移到 PostgreSQL

SQLite 单文件模型在**个人服务器自托管**场景下已到天花板：

- 多写者并发是结构需求而非偶发：Android App 多设备同步 + WebUI 操作 + 后台任务（导入/导出/还原/缓存清理/下载）天然并发写
- SQLite 的解法（缩短事务、拆批）只是把锁竞争窗口压小，无法消除「单写者」这一结构性限制；后续每次新增长写任务都要重蹈覆辙
- 已上全尺寸 PC server（性能余量足），PostgreSQL 多写者并发 + MVCC 是常规解

## 影响面与实施步骤

### 阶段 1：JPA 层 + 数据源（低风险，可独立回退）

- `application.yml`：`url` 换 `jdbc:postgresql://...`，`driver-class-name` 换 `org.postgresql.Driver`，`database-platform` 换 `PostgreSQLDialect`（Hibernate 6 内置，移除 `hibernate-community-dialects` 依赖）
- 依赖：新增 `org.postgresql:postgresql`，保留 `org.xerial:sqlite-jdbc`（EhImportService 读**用户上传的 EhViewer 备份 db** 仍用 sqlite-jdbc，这是读外部文件，与主库引擎无关）
- 实体（17 张表）：主键均 `GenerationType.IDENTITY`——PG 支持 `GENERATED ALWAYS AS IDENTITY` 列，Hibernate PG 方言直接兼容，**实体注解零改动**
- `ddl-auto: update` 照常（首次启动建表 + 索引）
- 移除 SQLite 特有参数：`journal_mode=WAL`、`busy_timeout`
- 回退条件：改动收敛在数据源配置 + 依赖，不触碰业务代码

### 阶段 2：部署形态

- systemd：`anotherviewer-web.service` 增加 `ANOTHERVIEWER_DB_URL` 指向本机 PG；PG 作为同机服务（`sudo apt install postgresql`）或独立实例
- Docker：`docker-compose.yml` 增加 `postgres` 服务（volume + healthcheck），`anotherviewer` 依赖其就绪；`ANOTHERVIEWER_DB_URL=jdbc:postgresql://postgres:5432/anotherviewer`
- 连接池：HikariCP 默认即可，PG 多连接写并发无需调小池（与 SQLite 相反）
- 用户/库初始化：启动脚本（`scripts/` 或 entrypoint）执行 `CREATE ROLE/CREATE DATABASE`（幂等）

### 阶段 3：备份/迁移契约重设计（主要工作量，ADR-0002 修订）

现状（ADR-0002 + `contracts/backup-format.md`）完全建立在「db = 单文件」之上：

- 导出：`VACUUM INTO` 产 `anotherviewer.db` 一致性快照 → 分片 7z 打包
- 还原：解包 → 逐片验哈希 → 文件替换（旧文件改名 `.bak`）→ 重启生效
- 迁移：拷贝 data-dir 即迁移

PG 下三选一（写入修订后的 backup-format 契约，`formatVersion` 升 2）：

1. **pg_dump 逻辑导出**（推荐）：`pg_dump --format=custom` 产一致性 dump 文件，替代 `VACUUM INTO` 快照进分片；还原用 `pg_restore`。优点：一致性由 PG 保证、工具成熟；缺点：还原需目标 PG 存在
2. **SQL 层导出**：JPA 按表 SELECT 序列化为可移植格式（如 JSON 行集）打包。优点：跨 PG 版本/方言稳健、可增量；缺点：需自行处理事务一致性（`REPEATABLE READ` 单快照读取）、实现量最大
3. **保留 SQLite 做备份载体**：备份时把 PG 数据灌入临时 SQLite 文件沿用旧契约。优点：备份产物形态不变、还原逻辑最小改动；缺点：双重引擎维护、导出实现 = 方案 2 + SQLite 写入

约束（迁移后仍成立）：

- 分片仍是可独立存储/传输/校验的实体（ADR-0002 核心语义不变）
- 还原仍是破坏性操作、需鉴权（Bearer token）
- `server_config` 仍是配置唯一权威（config.json 回写逻辑不变）

### 阶段 4：测试

- `SqliteIndexDdlTest`：改测试数据源——H2 PG 兼容模式（`MODE=PostgreSQL`）或 testcontainers PG
- `EhImportServiceTest`：不依赖主库引擎（读上传 sqlite 文件），**零改动**
- `BackupServiceTest`：随备份契约重写更新
- 新增：迁移数据校验测试（SQLite 全量数据 → PG 后行数/哈希一致）

## 迁移路径（数据搬迁）

1. 双写窗口（可选，最长规避停机）：SQLite 继续服务，后台任务把数据灌入 PG，切换读写后校验
2. 停机迁移（推荐，个人服务器规模小）：停服务 → pg_dump/灌入脚本搬数据 → 切 `ANOTHERVIEWER_DB_URL` → 起服务 → 行数/哈希校验
3. 回退：`ANOTHERVIEWER_DB_URL` 指回 SQLite，旧 data-dir 保留即可（迁移不删旧库）

## 非目标

- 不引入多租户/多用户（ADR-0001 单用户假设不变）
- 不做数据库水平分片（PC server 单实例足够）
- 不更换 App 端存储（App 端 SQLite 不变，经同步协议与服务器交互，与主库引擎解耦）

## 推论

- 本次临时修复（分批提交）保留为长期代码：PG 下短事务仍是最佳实践，且保底任何未来引擎
- `sqlite-jdbc` 依赖长期保留（EhImportService 读上传备份文件）
- 迁移完成前，`contracts/backup-format.md` 的 `formatVersion` 维持 1，旧备份仍可还原；阶段 3 落地后升 2
