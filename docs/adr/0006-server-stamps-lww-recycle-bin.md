# 0006: 服务器权威打戳、纯 LWW 与 7 天回收站（同步子系统 v4）

将同步子系统的冲突仲裁从「客户端打戳 + 策略分级 + ±5s skew 窗口」整体翻转为「服务器单调时钟统一打戳 + 纯时间戳 LWW（同戳后到胜）」，删除安全性改由 7 天确定性回收站兜底；`contracts/sync-conflict-rules.md` 升 v4.0，WebSocket 契约升 1.3.0（`/topic/sync` 转实现）。

## 背景：三端审计发现

对 Android App / WebUI / 服务器三端的同步实现审计发现五个结构性缺陷：

- **混合时钟域高水位可静默丢数据**: `lastModified` 由各端本地时钟生成并按高水位递增，服务器原样落库。两台设备时钟不一致时，时钟快的一端产生的旧数据可携带比时钟慢一端的新数据更大的时间戳，后续 pull/merge 按戳比较会**静默压制真实的新写入**——无告警、无冲突痕迹。
- **±5s skew 窗口丢编辑**: v1–v3 的 LWW 判据把 5 秒内的两次编辑视为「同时」，落入实体专属 tie-breaker（history.time / bookmark.page / first-received-wins）。用户在两台设备上先后快速编辑同一实体时，先到的编辑可以胜出——**后做的编辑丢失**。
- **LWW 受设备时钟漂移支配**: 即便不在 skew 窗口内，比较的也是两个互不可信的本地时钟；漂移几秒到几分钟的个人设备让「时间戳新」与「写入晚」彻底脱钩。
- **墓碑无 GC 无限膨胀**: v2/v3 的墓碑清理是「30 天 MAY purge」——可选、从未实现，且「所有已知设备均已删除」的前置条件在设备更替后无法判定。墓碑行只增不减。
- **soft 删除不传播与用户直觉相悖**: 策略 B 下 favorite/download 等软删除「单边删除不传播」，用户在一台设备删除后，另一台设备的 pull 会把记录**复活回来**；用户视角这是「删不掉」。

**用户裁决（2026-10-11）**: 本项目为个人 LAN 部署——① 不做多用户/多租户；② 不做平台分级（A/C 策略是过度设计）；③ 接受纯时间戳 LWW；④ 以服务器时钟为唯一时间基准；⑤ 同步节拍 5–10s 高频静默进行；⑥ 删除安全性不靠协议复杂度，由回收站兜底。

## 决策

- **D1 服务器单调时钟统一打戳**: 所有实体行的 `lastModified` 与 `serverTimestamp` 由服务器在写入时刻打戳（push 落库与 WebUI REST 直写同规则）；客户端上送值不落库，仅作传输元数据。后写严格 `>=` 先写，同域比较无 skew。业务时间字段（`history.time` 等）不被改写。
- **D2 默认 `lww`（同戳后到胜）**: `conflictStrategy` 缺省值从 `device_priority` 改为 `lww`；判据为 `incoming.lastModified >= existing.lastModified`，相等（同戳）= 后到者胜。±5s skew 窗口废除；`SKEW_TOLERANCE` 仅保留给 A/C 策略同平台回退分支。A/C 代码级保留（存量显式配置仍生效），服务器初值 `lww`，App 未显式设置时不覆盖。
- **D3 删除即记录，参与 LWW**: 墓碑不再有「恒传播 / 恒不传播」的分类特判——墓碑就是一行普通记录，按服务器戳参与同一套 LWW：新墓碑胜旧活行 → 删除传播；新活行胜旧墓碑 → 复活。删除是否传播只取决于打戳先后。
- **D4 7 天回收站**: 全部 7 类实体（favorites/history/downloads/bookmarks/filters/quickSearches/downloadLabels）的墓碑进入回收站，保留 `RECYCLE_RETENTION_DAYS = 7`（计时 = 墓碑 `lastModified`），WebUI 与 App 双入口；`GET /api/v1/recycle/list`、`POST /api/v1/recycle/restore`、`POST /api/v1/recycle/purge`。**恢复不是特判路径**——restore = 写入 `deleted=false` + 服务器新戳的普通 LWW 记录，传播到全部设备。ehSession 例外：登出墓碑不进回收站 UI，到期静默清除（凭据不留可恢复副本）。v2/v3 的「30 天 MAY purge（`TOMBSTONE_PURGE_DAYS`）」废除。
- **D5 下载内容进 trash**: 下载实体条目 purge 时，其文件移入 `<dataDir>/trash` 再保留 7 天后物理清除；restore 在 trash 保留期内可完整恢复文件引用。
- **D6 `/topic/sync` 实时通知**: `sync.update` 转实现（WebSocket 契约 v1.3）——push 事务提交后（AFTER_COMMIT）发布，每次 push 至多一条，无节流，envelope `version: "1.3"`；`entities` 为 8 类联合（含 preferences）。`sync.conflict` 类型删除：v4 无冲突通知。客户端收到后失效对应实体缓存并重拉（幂等吸收 1 秒重叠窗口）。
- **D7 App 默认 10s 节拍**: Android 端自动同步默认间隔改为 10 秒静默节拍（前台），配合 pull 的 `since - 1000` 重叠窗口，删除/编辑在秒级收敛；`AUTO_SYNC_INTERVAL_SEC_DEFAULT=900` 的批量拉取仍作为后台兜底。

## 影响面与实施步骤

### 三端改动概要

- **服务器**: 写路径统一改为落库前服务器打戳（push merge 与 REST 直写共用）；merge 判据改 `>=` 同戳后到胜，删除仲裁统一 D3；新增 recycle 三端点与到期清理任务；`/topic/sync` 发布器（AFTER_COMMIT）；SyncPolicy 初值 `lww`。存量数据无需刷戳——旧客户端戳的行在被首次覆盖时自然收敛到服务器时钟域（收敛窗口内由回收站兜底误删）。
- **Android App**: 上送 `lastModified` 降级为传输元数据（不再维护高水位）；本地 merge 判据对齐 v4；自动同步默认 10s 节拍；新增回收站入口（list/restore）。
- **WebUI**: REST 直写路径确认走服务器打戳；新增回收站页面；收到 `sync.update` 失效对应实体列表缓存并重拉。

### 兼容矩阵

| 组合 | 行为 |
|---|---|
| 旧 App × 新服务器 | App 忽略 policy；其上送的客户端戳被服务器打戳覆盖，**无害**；旧 App 的残留行重推时，服务器为其打新戳，若为墓碑则新墓碑戳压制各端旧活行、删除得以收敛——**旧 App 残留行重推被服务器新墓碑戳压制后收敛** |
| 新 App × 旧服务器 | GET/PUT policy 404 → 无 policy 可取，回退旧行为（客户端打戳 + B），不报错 |
| 新 App × 新服务器 | 全量 v4 语义 |

### 迁移

- **策略 marker 一次性迁移**: 升级例程向 `server_config` 写入 `syncPolicy.conflictStrategy = lww` 的 marker。**风险声明**: 该迁移会**覆盖显式 `device_priority` 配置**——已显式选择 A/C 的存量部署升级后默认变为 `lww`（这正是本 ADR 的意图，但对依赖 A 删除语义的用户是一次行为变更，发布说明必须明示；仍可在 WebUI 高级面板改回，但会被下一次 android push 携带的 policy 覆盖，D2 语义不变）。
- 数据不迁移：打戳域收敛依赖自然覆盖（见上），墓碑存量直接进回收站计时（以其现有 `lastModified` 起算，7 天内未到期即可 restore）。

### 放弃的备选

- **客户端时钟 + 偏移估计（NTP/Cristian 算法）**: 需要每设备可靠测出对服务器时钟的偏移并处理跳变；个人 LAN 下设备质量参差，估计误差与故障模式（休眠唤醒、时钟跳变）比「服务器统一打戳」复杂得多，且仍需为估计失败兜底——不如把打戳权收到服务器。
- **30 天软 GC（保留 v2/v3 MAY purge 并实现它）**: 只解决膨胀不解决误删——30 天后静默硬删与用户「我删的东西还能找回吗」的预期不符；7 天确定性回收站把「是否删除」的最终决定权留给用户，语义可解释、UI 可呈现。

## 非目标

- 不引入多用户/多租户（ADR-0001 单用户假设不变）
- 不做跨服务器/多服务器同步（单实例个人服务器）
- 不保证删除的即时强一致（10s 节拍 + 拉取式收敛足够；回收站兜底窗口内误删可恢复）

## 推论

- `contracts/sync-conflict-rules.md` v4.0 为仲裁唯一权威；`SKEW_TOLERANCE` / `TOMBSTONE_PURGE_DAYS` 仅以历史标注形式存在
- `contracts/websocket-protocol.md` 1.3.0：`/topic/sync` 转实现、`sync.conflict` 删除
- `contracts/sync-schemas.json` / `contracts/openapi.yaml`：新增 `recycle*` defs/schema 与三端点，`SyncPolicy` 缺省 `lww`
- 后续若做多用户，回收站与打戳语义按用户维度扩展即可，协议结构不变
