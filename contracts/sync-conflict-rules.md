# AnotherViewer Sync Protocol — Conflict Resolution Rules

> Version: 4.0
> Date: 2026-10-11
> Schema: `contracts/sync-schemas.json` (draft 2020-12)
> ADR: `docs/adr/0003-three-party-model-sync-policy.md`、`docs/adr/0004-eh-session-in-sync.md`、`docs/adr/0006-server-stamps-lww-recycle-bin.md`
>
> v4.0 变更摘要（相对 v3.0，ADR-0006）：**服务器打戳取代客户端打戳**——§1.3 整节反转，所有 `lastModified`/`serverTimestamp` 均由服务器单调时钟生成，客户端上送值不落库、仅作传输元数据；**默认策略改 `lww`**（ADR-0006 D2，个人 LAN 单用户部署、不做平台分级）；**B 判据改为「同戳后到胜」**（`incoming.lastModified >= existing.lastModified`），±5s skew 窗口废除（`SKEW_TOLERANCE` 仅保留给 A/C 策略同平台回退分支）；**删除语义统一**——§3.8 重写，墓碑行按服务器戳参与 LWW，soft/tombstone 分类仅保留 purge 语义差异；**7 天确定性回收站取代 30 天 MAY purge**（§4.1/4.2 改写；`TOMBSTONE_PURGE_DAYS` 废除，新增 `RECYCLE_RETENTION_DAYS=7`）；**新增 §9 回收站**（`/api/v1/recycle/list|restore|purge`）。v3.0 的 ehSession 实体与 v2.0 的 SyncPolicy 机制完整保留（缺省值与仲裁判据随 v4 更新）。
>
> v3.0 变更摘要（相对 v2.0）：新增 ehSession 同步实体（EH 登录会话 + 用户设置，ADR-0004）；单例实体 LWW 双向同步（±5s skew），**不参与 conflictStrategy**（与 preferences 同级，ADR-0001/0003 先例）；登出/清 cookie = tombstone，任何策略下删除传播（§3.8 tombstone 实体）；Web 端 cookie 加密落库（`enc:v1:` + security.key）。v2.0 语义完整保留。（历史注：v3.0 的 ±5s skew 判据、tombstone 恒传播特判与客户端打戳语义自 v4.0 起被 §1.2/§1.3/§3.8 取代。）
>
> v2.0 变更摘要（相对 v1.0）：引入可配置冲突策略 `conflictStrategy`（device_priority | lww | web_priority，默认 device_priority〔历史注：v4.0 起默认改 `lww`，见 ADR-0006〕）；merge 全部参数化；A/C 下同键冲突无条件优先级胜；soft 实体删除传播按策略（§3.8）；tombstone 实体删除任何策略下传播〔历史注：v4.0 起统一按服务器戳 LWW，见 §3.8〕；新增 §8 SyncPolicy 与 `/api/v1/sync/policy` 端点；§5.1 仲裁总序重写。v1.0 语义 = 策略 `lww`（B），完整保留为回退兜底。

## 1. Timestamp Format & Clock Domain

### 1.1 Timestamp Format

All timestamps in the sync protocol are **epoch milliseconds** (Unix timestamp × 1000), stored as JSON integers. Fields: `time`, `lastModified`, `timestamp`, `serverTimestamp`, `lastSeen`, `lastSyncTimestamp`.

### 1.2 Clock Domain & Monotonicity（v4 重述：统一服务器时钟域）

**v4 起所有参与仲裁的时间戳出自同一时钟域——服务器单调时钟**（ADR-0006 D1）：

- **单一时钟域**: 每个实体行的 `lastModified` 与 pull 响应的 `serverTimestamp` 均由服务器在写入/响应时刻用自身单调时钟生成。后写的行严格 `>=` 先写的行（单调性由服务器时钟保证），同域比较**不存在 clock skew 概念**。
- **SKEW_TOLERANCE 仅余回退用途**: ±5s 同时窗口仅保留给策略 A/C 的**同平台回退分支**（两个同平台记录比较时的历史语义，§3.1–§3.7）；策略 B（默认）与全部删除仲裁（§3.8）不使用 skew 窗口。
- **NTP recommendation**: Server SHOULD run NTP（回收站 `remainingDays` 计算与 UI 展示依赖墙上时钟；打戳单调性由服务器实现自行保证——保证打戳用时钟不回拨，见 ADR-0006）。
- **Monotonic guard（v4 语义）**: 「同实体新 push 的时间戳不得早于存量」不再由客户端本地高水位保证（v3 语义废除），而是服务器时钟的固有性质：后落库的行必然获得 `>=` 先落库的时间戳。

### 1.3 lastModified 来源规则（v4 反转：服务器权威打戳）

**规则**: 每个实体行的 `lastModified` 由**服务器**在写入时刻以其单调时钟打戳；客户端上送的 `lastModified` 值**不落库**，仅作为传输元数据（可用于日志/诊断）。ADR-0006 D1。

- **push 落库打戳**: `POST /api/v1/sync/push` 合并落库的每一行（新增/覆盖/墓碑/复活），其 `lastModified` 均为服务器打戳值（`entity.lastModified = serverNow()`），覆盖任何客户端上送值。
- **WebUI REST 直写同样打戳**: WebUI 经 REST 端点直接写实体（删除历史、收藏、偏好设置等）时，同样由服务器时钟打戳——push 与 REST 直写在同一时钟域，天然可比。
- **迁移语义**: v3 及之前的存量行保留旧客户端戳（可能与服务器时钟域存在偏差）；这些行在被任何新写入（push/REST/墓碑/复活）覆盖时获得服务器戳，此后收敛到单一时钟域。收敛前的跨域偏差窗口由回收站兜底（§4/§9）。
- **业务时间字段不被改写**: 实体自身的业务时间（`history.time`、`bookmark.time`、`favorite.time`、`download.time` 等）是用户数据；服务器打戳只作用于 `lastModified` 同步元数据字段，**绝不**改写业务时间字段。
- **preferences 同受此规则**: preferences 的 `lastModified` 同样由服务器打戳（客户端上送值不落库）；判定为同戳后到胜（§5.1），不参与 conflictStrategy（单用户设置，后同步者即最终意图，见 ADR-0001）。
- **LWW 语义（策略 B，v4 默认）**: 双行 `lastModified` 直接比较（同一服务器时钟域，无 skew）：`incoming.lastModified >= existing.lastModified` → incoming 胜；相等（同戳）= 后到者胜（§5.1/§5.2）。
- **到达戳（merge 的 incoming 侧取值）**: push 到达时服务器**先**以单调时钟为整批入参重打戳 `lastModified`（每请求一个到达戳），merge 比较中的 `incoming.lastModified` 即该到达戳——与存量行同域；客户端上送值在任何路径都不参与仲裁。时钟漂移不影响胜负；胜负严格等于服务器收到先后（`clock.next()` 单调 ⇒ 后到批的到达戳大于先到批）。A/C 同平台回退分支的比较同样使用到达戳。

### 1.4 Conflict Strategy（conflictStrategy，v2 新增；v4 默认值变更）

策略定义（`SyncPolicy.conflictStrategy`，§8；ADR-0003 D1；v4 默认值变更见 ADR-0006 D2）：

| 值 | 语义 | 平台序 |
|---|---|---|
| `device_priority` (A) | 同键冲突 Android 胜（**v4 起代码级保留，非推荐**） | android > web |
| `lww` (B，**v4 起默认**) | 服务器戳 LWW，同戳后到胜（§1.3/§5.1） | 无 |
| `web_priority` (C) | 同键冲突 Web 胜（**v4 起代码级保留，非推荐**） | web > android |

- **平台判定**: `deviceId` 前缀（§7 `DEVICE_ID_FORMAT`，首个 `-` 之前）：`android-*` → android；`web-*`/`server-*` 等 → web 侧。
- **无条件优先（仅 A/C）**: A/C 下，同键（idempotency key）跨平台冲突**无条件**由优先端胜，不比较 `lastModified`。不为长期离线场景做保护设计（ADR-0003）；时间戳仅用于高水位/增量 pull/展示。v4 默认部署不使用 A/C（个人 LAN、单用户、无平台分级，ADR-0006）；代码级保留仅为兼容已显式配置旧策略的存量部署。
- **同平台同键**（如两个 android 设备）: 回退 B 语义（v4 = 服务器戳 LWW，同戳后到胜；`SKEW_TOLERANCE` 仅在此分支保留，§1.2）。
- **切换语义**: 策略即时生效于其后的 merge；**不追溯**重合并已收敛数据（B→A 后需在权威端重做删除才能传播；UI tooltip 明示）。
- **策略权威**: Android App（ADR-0003 D2，§8）；v4 默认值 `lww` 由服务器初值提供，App 未显式设置时不覆盖。

## 2. Idempotency Keys

Each entity type has a natural idempotency key that identifies a unique logical record across devices. The server uses these keys to detect duplicates during push and to match records during merge.

| Entity | Idempotency Key | Rationale |
|--------|----------------|-----------|
| Favorite | `gid` | A gallery can appear in local favorites at most once |
| History | `gid` | One history record per gallery (updated on re-view) |
| Download | `gid` | One download task per gallery |
| Bookmark | `gid` | One reading-progress bookmark per gallery |
| Filter | `(mode, text)` | Mirrors `Filter.equals()` / `hashCode()` in Android source |
| QuickSearch | `name` | User-facing unique name for the preset |
| DownloadLabel | `label` | Label names are unique in the Android UI |
| EhSession | *单例（无自然键）* | 每用户至多一条活记录；服务器固定键（v3，ADR-0004） |

### 2.1 Idempotent Push

When the server receives a push, it upserts by idempotency key:

```
function upsertEntity(incoming, existingByKey, strategy):
    key = idempotencyKey(incoming)
    existing = existingByKey[key]
    if existing is null:
        store(stamp(incoming))   // v4: 落库前由服务器打戳（§1.3）
    else:
        merged = merge(existing, incoming, strategy)   // §3 / §3.8
        store(stamp(merged))     // v4: 服务器打戳
```

Retrying the same push with identical payloads is a no-op（重试行获得新服务器戳但仲裁结果不变；v4 下重复行因同戳后到而胜出，落库内容等价——客户端幂等吸收，§6.2）。

## 3. Per-Entity Merge Strategies

所有 merge 函数签名含 `strategy`（§1.4）。**策略 B（v4 默认）下全部函数等价服务器戳 LWW（同戳后到胜，无 skew 窗口）**。删除 vs 保留的跨策略总规则见 §3.8；各实体节仅描述实体专属部分。

### 3.1 Favorites (LocalFavoriteInfo) — Union Merge

**Rule**: 存在性按 §3.8 union/删除规则；元数据（favoriteSlot、title 等）同键双活时按策略：B（默认）= 服务器戳 LWW（同戳后到胜）；A/C = 优先端记录无条件胜（同平台回退 LWW）。

```
function mergeFavorite(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    // both alive
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)  // §3.8
    return lwwServerStamp(existing, incoming)   // v4 B: >= 后到胜
```

**Behavior (B，v4 默认)**: If device A removes a favorite and device B still has it, the favorite survives **除非**删除方的墓碑行持有不更旧的服务器戳（§3.8——v4 起单边删除不再恒不传播，是否传播只取决于打戳先后）。**(A/C)**: 优先端删除无条件传播；非优先端删除在优先端持有该键时不传播（§3.8）。

### 3.2 History (HistoryInfo) — Last-Write-Wins + Tombstone

**Rule**: 服务器戳更新（或相等后到）的写入胜（B，v4 默认）。A/C 同键双活 = 优先端无条件胜。

```
function mergeHistory(existing, incoming, strategy):
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    // v4 B（默认）：同域服务器戳直接比较，同戳后到胜（±5s skew 窗口废除）
    if incoming.lastModified >= existing.lastModified:
        return incoming
    if strategy == B:
        return existing
    // —— 以下仅 A/C 同平台回退分支可达（§1.2），历史 tie-breaker 仅此处保留 ——
    if incoming.time > existing.time:
        return incoming   // 业务 time（view time）晚者胜
    return existing
```

**Delete behavior**: Tombstone（§3.8：v4 起按服务器戳参与 LWW）。A `deleted: true` push 落库为墓碑行，`lastModified` 由**服务器打新戳**（v3 的 `max(存量, 推送方)` bump 废除，§1.3）；无存量行时墓碑作为新行落库。增量 pull（`since > 0`）把墓碑传播到其他设备，客户端收到 `deleted: true` 即删除本地行。Resurrection（墓碑 vs 活行）按 §3.8。

### 3.3 Downloads (DownloadInfo) — Union Merge + Status Sync

**Rule**: 存在性按 §3.8；mutable fields (`state`, `finished`, `downloaded`, `label`)：B（默认）= 服务器戳 LWW（同戳后到胜）；A/C 双活 = 优先端记录胜。

```
function mergeDownload(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    winner = (crossPlatform and strategy != B)
             ? priorityRecord(existing, incoming, strategy)
             : lwwServerStamp(existing, incoming)   // v4 B: >= 后到胜

    // Immutable identity fields: keep from whichever record has them populated
    result = clone(winner)
    if result.archiveUri is null and other.archiveUri is not null:
        result.archiveUri = other.archiveUri
    return result
```

**Status sync specifics**:
- `state` transitions are NOT validated server-side. Winner per strategy.
- A download in `STATE_DOWNLOAD` (2) on one device and `STATE_FINISH` (3) on another resolves to the strategy winner.
- `finished`, `total` are progress counters that follow the winner.
- `downloaded` is **NOT a sync field**: session-scoped counter; the server has no `downloaded` column and never persists it.
- `label` is transmitted as the label **name** (string) and mapped to/from the server-side `download_label.id` at push/pull; a label name without a server row is auto-created on push.

### 3.4 Bookmarks / Reading Progress (BookmarkInfo) — Last-Write-Wins + Tombstone

**Rule**: B（v4 默认）= 服务器戳更新者胜（同戳后到胜）。A/C 同键双活 = 优先端无条件胜。

```
function mergeBookmark(existing, incoming, strategy):
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    // v4 B（默认）：同域服务器戳，同戳后到胜（±5s skew 窗口废除）
    if incoming.lastModified >= existing.lastModified:
        return incoming
    if strategy == B:
        return existing
    // —— 以下仅 A/C 同平台回退分支可达（§1.2），历史 tie-breaker 仅此处保留 ——
    if incoming.page > existing.page:
        return incoming   // 更高 page（更远阅读进度）胜
    return existing
```

**Delete behavior**: Tombstone（§3.8：v4 起按服务器戳参与 LWW），打戳与增量 pull 同 §3.2。

### 3.5 Filters (Filter) — Union Merge

**Rule**: 存在性/enabled 按 §3.8 + 策略；B（默认）= 服务器戳 LWW；additive bias 仅 A/C 同平台回退分支保留。

```
function mergeFilter(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    // v4 B（默认）：同域服务器戳，同戳后到胜
    if incoming.lastModified >= existing.lastModified:
        return incoming
    if strategy == B:
        return existing
    // —— 以下仅 A/C 同平台回退分支可达（§1.2）——
    if incoming.enabled != existing.enabled:
        return incoming if incoming.enabled else existing   // enabled=true (additive bias)
    return existing
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.6 Quick Searches (QuickSearch) — Union Merge

```
function mergeQuickSearch(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    return lwwServerStamp(existing, incoming)   // v4 B: >= 后到胜（无 skew 窗口）
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.7 Download Labels (DownloadLabel) — Union Merge

```
function mergeDownloadLabel(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    return lwwServerStamp(existing, incoming)   // v4 B: >= 后到胜（无 skew 窗口）
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.8 Delete/Resurrection Rules（v4 重写：删除仲裁统一）

**v4 起删除仲裁不再按实体分类分道**：无论 soft 还是 tombstone 实体，**墓碑行按其服务器戳参与 LWW**（§1.3 服务器打戳、§5.1 同戳后到胜；ADR-0006 D3「删除即记录」）：

```
function crossPlatform(a, b): platformOf(a.deviceId) != platformOf(b.deviceId)
function priorityPlatform(strategy): A -> "android", C -> "web", B -> null
function priorityRecord(a, b, strategy):
    return a if platformOf(a.deviceId) == priorityPlatform(strategy) else b

function mergeDeleteVsAlive(existing, incoming, strategy):
    if existing.deleted == incoming.deleted: return null   // 同态 → 实体专属规则
    tomb = existing.deleted ? existing : incoming
    live = existing.deleted ? incoming : existing

    // 仅显式配置 A/C 的存量部署：跨平台删除仲裁由平台序先行（§1.4）
    if strategy != B and crossPlatform(tomb, live):
        return priorityRecord(tomb, live, strategy)

    // v4 统一规则（含策略 B 与 A/C 同平台回退）：
    // 墓碑是一行普通记录，按服务器戳参与 LWW
    return tomb if tomb.lastModified >= live.lastModified else live
```

- **新墓碑胜旧活行**（`tomb.lastModified >= live.lastModified`）→ 删除传播：增量 pull 向全部设备下发墓碑，各端删除本地对应行。
- **新活行胜旧墓碑**（`live.lastModified > tomb.lastModified`）→ 复活：该行以 `deleted=false` 的普通 LWW 记录传播，全部设备恢复展示。
- v3 的两套特判**废除**：「tombstone 实体删除任何策略下恒传播」与「soft 实体 B 下单边删除恒不传播」均不再成立——删除是否传播只取决于服务器打戳先后。ehSession 登出墓碑同样按此规则仲裁（后写胜）。

**soft / tombstone 分类在 v4 仅保留 purge 语义差异**（§4/§9）：

| 分类 | 实体 | v4 purge 语义差异 |
|---|---|---|
| soft | favorite, download, filter, quickSearch, downloadLabel | 墓碑进回收站，可 restore（§9） |
| tombstone | history, bookmark | 墓碑进回收站，可 restore（§9） |
| tombstone（例外） | ehSession | 墓碑**不进回收站 UI**：到期由 purge 静默清除（凭据类数据不留可恢复副本，ADR-0006 D4 范围例外） |

**行为矩阵（B 列为 v4 语义；A/C 列仅显式配置旧策略的存量部署可达）**：

| 分歧场景 | A device_priority | B lww（v4 默认） | C web_priority |
|---|---|---|---|
| 同键同时编辑（双活） | android 记录胜 | 服务器戳 LWW，同戳后到胜 | web 记录胜 |
| 优先端删 / 非优先端保留 | 删除传播 | 墓碑与活行按服务器戳 LWW：新墓碑胜 → 删除传播；新活行胜 → 保留 | 删除传播 |
| 非优先端删 / 优先端保留 | 保留 | 同上（后写胜） | 保留 |
| 各删不同键（任意实体） | 双向传播（各自成墓碑） | 双向传播（各自成墓碑） | 双向传播（各自成墓碑） |
| 不相交新增 | union | union | union |
| 非优先端删 / 优先端无该键 | 删除传播（无冲突对象） | 删除传播（无冲突对象） | 同 A |

**Resurrection**（tomb 存量 vs live 推送）= 上表镜像：A/C 跨平台下优先端 live 胜非优先端 tomb；策略 B 与同平台回退下按服务器戳——新 live 复活旧 tomb，新 tomb 压制旧 live（§3.2/§3.4/§3.9）。

### 3.9 EH Login Session (SyncEhSession) — LWW Singleton + Tombstone（v3 新增；v4 判据更新）

**Rule**: 单例实体（每用户至多一条活记录，无自然 idempotency key，服务器固定键）。仲裁恒为 **LWW**（v4 = 服务器戳、同戳后到胜，§1.3），**不参与 conflictStrategy**——与 preferences 同级（ADR-0001/0003：单用户设置，后同步者即最终意图）；A/C 的平台序不适用。

```
function mergeEhSession(existing, incoming):
    // ehSession 不参与 conflictStrategy（ADR-0004 D2）
    // v4：服务器戳直接比较，同戳后到胜（±5s skew 废除）
    if incoming.lastModified >= existing.lastModified:
        return incoming
    return existing
```

- **Tombstone 语义**: 登出/清 cookie → `deleted=true` 墓碑，落库时**服务器打新戳**（§1.3），按 §3.8 统一规则参与 LWW（后写胜；v3 的「任何策略下恒传播」特判废除）；增量 pull 传播到其他设备。客户端收到墓碑即清除本地 cookie 并登出。**例外**：ehSession 墓碑不进回收站 UI，到期由 purge 静默清除（§4/§9）。
- **Provenance**: 沿用 §6.2——服务端按 `lastModified > since` 增量返回 ehSession（含墓碑）；`SyncPullResponse.entities.ehSession` 缺省/空 = 服务器无该会话或未变更。
- **字段**: `cookies[]`（name/value/domain/path/expiresAt/secure/httpOnly/persistent/hostOnly）、`displayName?`、`avatar?`、`gallerySite?`（0=e-hentai.org, 1=exhentai.org，null=不改对端）、`lastModified`、`deviceId`、`deleted`。仅收容站点域 cookie（e-hentai.org / exhentai.org / ehgt.org / forums.e-hentai.org 及子域）。
- **凭据安全**: Web 端 cookie value 加密落库（`enc:v1:` + security.key，ADR-0004 D4），仅进程内解密；App 端按平台安全存储。明文不落盘。

## 4. Soft-Delete vs Hard-Delete Policy

| Entity | Delete Type | 删除仲裁（v4，§3.8） | 回收站 | Rationale |
|--------|------------|----------------------|--------|-----------|
| Favorite | **Soft** | 服务器戳 LWW（后写胜） | 进站，可 restore | Union merge 语义保留在双活分支；误删由回收站兜底 |
| History | **Tombstone** | 同上 | 进站，可 restore | Explicit user action should propagate; 误删可恢复 |
| Download | **Soft** | 同上 | 进站，可 restore（文件随 purge 移 `dataDir/trash` 再保留 7 天，ADR-0006 D5） | One device clearing its list must not silently destroy another's downloads without recourse |
| Bookmark | **Tombstone** | 同上 | 进站，可 restore | Clearing reading position is explicit user intent; 误删可恢复 |
| Filter | **Soft** | 同上 | 进站，可 restore | Additive bias — filters are safety/UX features |
| QuickSearch | **Soft** | 同上 | 进站，可 restore | User-created presets; 误删可恢复 |
| DownloadLabel | **Soft** | 同上 | 进站，可 restore | Removing on one device shouldn't break another's organization |
| EhSession | **Tombstone** | 同上 | **不进站**（凭据类，到期 purge 静默清除；ADR-0004 D3 / ADR-0006 D4 范围例外） | Logout / cookie clear propagates; 不留可恢复的凭据副本 |

### 4.1 删除生命周期与回收站（v4 重写）

1. Device sets `deleted: true` 并 push（或 WebUI 经 REST 直删）。服务器将行置为墓碑并以**服务器单调时钟打新戳**（客户端上送值不落库，§1.3）。
2. 墓碑按 §3.8 参与 LWW：新墓碑胜旧活行 → 删除经增量 pull 传播到全部设备，各端 UI 隐藏该实体。
3. **回收站保留期**: 墓碑自其 `lastModified`（服务器打戳时刻）起保留 `RECYCLE_RETENTION_DAYS = 7` 天；期间用户可经 §9 端点 list/restore/purge（WebUI 与 App 双入口）。
4. **Restore**: `POST /api/v1/recycle/restore` → 行置 `deleted=false` 并由服务器打新戳 = 一条普通 LWW 记录，按 §3.8/§6 传播到全部设备（复活）。
5. **Purge**: 超过保留期的墓碑由 `POST /api/v1/recycle/purge`（或到期自动清理）硬删除；下载内容实体 purge 时其文件移入 `dataDir/trash` 再保留 7 天（ADR-0006 D5）；ehSession 墓碑不进回收站 UI，到期静默清除。
6. **历史机制废除**: v3/v2 的「30 天 MAY purge（`TOMBSTONE_PURGE_DAYS`）」自 v4 起由 7 天确定性回收站取代（§7 常量表历史标注）。

### 4.2 Tombstone Lifecycle (History / Bookmark) — v4 收敛进回收站

1. Device sets `deleted: true` and pushes to the server. 服务器置墓碑并**服务器打新戳**（v3 的 `max(存量, 推送方)` bump 废除——打戳权在服务器，§1.3）；无行时墓碑作为新行落库。
2. On incremental pull (`since > 0`), other devices receive the tombstone and delete their matching local records（若其后有更晚服务器戳的活行写入则复活，§3.8）。
3. 墓碑进入回收站，7 天保留期（计时 = 墓碑 `lastModified`），后续 list/restore/purge 见 §4.1 step 3–5 与 §9。

## 5. Simultaneous Edit Arbitration

v4 起不存在「时钟 skew 窗口内的同时编辑」概念——所有 `lastModified` 同域（服务器单调时钟，§1.2），任意两行必有严格先后或相等。相等（同戳）时按到达先后裁决（后到胜）。

### 5.1 General Rule（v4 仲裁总序）

仲裁总序 = **① 策略序（仅 A/C 跨平台同键，无条件） → ② 服务器戳比较（B，默认：`>=` 后到胜）**。

1. **策略序（仅 A/C，v4 默认部署不启用）**: A → android 记录胜；C → web 记录胜。不比较时间戳、不看收到顺序。
2. **服务器戳比较（B，默认）**: `incoming.lastModified >= existing.lastModified` → incoming 胜。服务器按收到顺序串行落库（§5.2），后落库者戳 `>=` 存量且胜出——**同戳（相等）= 后到者胜**。**B 下没有任何其他 tie-break**：v3 的 first-received-wins 与 history.time / bookmark.page / filter.enabled 实体专属 tie-breaker 全部废除，仅在 A/C 同平台回退分支保留（§3.2/§3.4/§3.5）。
3. **Union 实体存在性**: 不同键各自成行，union 不变。

### 5.2 Server Processing Order

The server MUST process pushes sequentially per idempotency key (serialize writes to the same key). Concurrent pushes to different keys MAY be processed in parallel. 串行落库保证「后到者获得 `>=` 的戳并胜出」与打戳单调性一致（§1.2）。

```
// Server push handler (per entity)
lock(idempotencyKey):
    existing = store.get(key)
    merged = merge(existing, incoming, currentStrategy)   // §3 / §3.8
    store.put(key, stamp(merged))    // v4: 服务器打戳（§1.3）
```

### 5.3 Conflict Notification

The server does NOT notify clients of conflicts（v4 起 WebSocket 亦无 `sync.conflict` 通知类型——该类型自契约中删除，见 `contracts/websocket-protocol.md` §3.4）。The merge is deterministic and both devices will converge on the next pull. Clients MAY subscribe `/topic/sync` 的 `sync.update`（v1.3，实现于 push 事务提交后）感知「有变更」并触发增量 pull；合并本身对用户透明，无冲突 UI。

## 6. Incremental Sync Protocol

### 6.1 Push (Client → Server)

```
POST /api/v1/sync/push
Content-Type: application/json
Authorization: Bearer {api-token}

Body: SyncPushRequest (see sync-schemas.json)
```

- Client sends ALL entities modified since its last successful sync.
- Client tracks `lastSyncTimestamp` locally (persisted in SharedPreferences / Room).
- Each entity carries its own `lastModified` and `deviceId`——**v4**: 客户端 `lastModified` 仅作传输元数据（不落库，服务器落库时打戳，§1.3）。
- **v2**: `SyncPushRequest.policy`（可选）——android 平台 push 携带时为权威覆盖（§8 / D2）。

### 6.2 Pull (Server → Client)

```
GET /api/v1/sync/pull?since={lastSyncTimestamp}
Authorization: Bearer {api-token}

Response: SyncPullResponse (see sync-schemas.json)
```

- Server returns all entities with `lastModified > since`. **v4**: `since` 与行 `lastModified` 同为服务器时钟域（§1.2），比较无 skew。服务器实现含 **1 秒重叠窗口**（实际按 `since - 1000` 过滤）以防并发提交在窗口边界漏行；重叠下发的少量重复行由客户端 merge 幂等吸收（§2.1——同键重放仲裁结果不变）。
- Client merges received entities into local storage using the same merge rules（含 v2 strategy，取 `SyncPullResponse.policy`，缺省 `lww`；旧服务器无 policy 字段 → 客户端回退 B 且不报错——v4 默认即 B）。
- Client updates its `lastSyncTimestamp` to `serverTimestamp` from the response（与行 `lastModified` 同域，§1.2）。

### 6.3 Sync Cycle

A full sync cycle is: **push → pull → apply**.

```
function syncCycle():
    pushResult = POST /api/v1/sync/push { localChanges, deviceId, now(), policy? }
    pullResult = GET /api/v1/sync/pull?since={lastSyncTimestamp}
    strategy = pullResult.policy?.conflictStrategy ?? "lww"   // v4 缺省 lww
    for each entity in pullResult.entities:
        localStore.merge(entity, strategy)   // using per-entity merge strategy
    lastSyncTimestamp = pullResult.serverTimestamp
```

## 7. Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `SKEW_TOLERANCE` | 5000 ms | **仅 A/C 策略同平台回退分支使用**（v4 起 B 与删除仲裁为同域服务器戳比较，不用 skew 窗口，§1.2） |
| `TOMBSTONE_PURGE_DAYS` | 30 | **v4 起废除**：30 天 MAY purge 由 `RECYCLE_RETENTION_DAYS=7` 确定性回收站取代（§4/§9）；仅存留为历史标注 |
| `RECYCLE_RETENTION_DAYS` | 7 | 回收站保留期（v4 新增，§9）；计时 = 墓碑行 `lastModified`（服务器打戳时刻） |
| `MAX_DEVICE_ID_LENGTH` | 128 chars | Maximum length of deviceId string |
| `DEVICE_ID_FORMAT` | `{platform}-{uuid}` | e.g. `android-550e8400-e29b-41d4-a716-446655440000` |
| `PRIORITY_ORDER` | android > web | 平台序常量（§1.4；仅 A/C 仲裁依据，v4 默认部署不启用） |
| `STRATEGY_DEFAULT` | `lww` | conflictStrategy 缺省值（**v4 起默认 `lww`**，ADR-0006 D2） |
| `CLIENT_TIER_DEFAULT` | 1 | clientTier 缺省（同步+流式） |
| `AUTO_SYNC_INTERVAL_SEC_DEFAULT` | 10 | 自动同步间隔缺省（**v4 起 10s 静默节拍**，ADR-0006 D7）；0=仅网络变化触发 |
| `PULL_OVERLAP_WINDOW_MS` | 1000 | pull 的 since 重叠窗口（`since - 1000`），防并发提交漏行；客户端幂等吸收（§6.2，v4 新增） |

## 8. SyncPolicy（v2 新增；v4 默认值更新）

Schema: `sync-schemas.json#/$defs/syncPolicy` = `SyncPolicy{conflictStrategy, clientTier, autoSyncIntervalSec}`。

- **端点**: `GET /api/v1/sync/policy` 返回当前策略；`PUT /api/v1/sync/policy` 设置（任一已认证客户端）。OpenAPI: `contracts/openapi.yaml`。
- **pull 附 policy**: `SyncPullResponse.policy` 携带当前 SyncPolicy；客户端据此执行 merge（§6.2）。
- **push 附 policy（D2 权威）**: android 平台 push 的 `SyncPushRequest.policy` 服务端必须持久化（等价 PUT）；WebUI 的 PUT 允许但会被下一次 android push 覆盖，WebUI 高级面板须明示该语义。v4 起服务器初值为 `lww`，App 未显式设置时不覆盖。
- **clientTier**（D3）: 0=独立 / 1=同步+流式（默认） / 2=浏览代理 / 3=下载托管（押后）。档位由 App 选择并随 policy 声明；Tier-2/3 路由使用 `WebUiTier2ProxyInterceptor` 拦截器模式。
- **autoSyncIntervalSec**（D4）: 网络感知自动同步间隔；0=仅网络变化触发；新网络不自动配对。
- **兼容矩阵**: 旧 App×新服务器 → App 忽略 policy，服务器按 App 无 policy push 处理，收敛于服务器视图（客户端戳被覆盖无害，ADR-0006 兼容矩阵）；新 App×旧服务器 → GET/PUT policy 404，App 回退 B，不报错；B = 完整回退兜底。
- **客户端义务**: 所有客户端 MUST 忽略 SyncPolicy/pull/push 中未知字段（前向兼容）。

## 9. Recycle Bin（v4 新增，ADR-0006 D4）

删除安全兜底：所有实体墓碑进入回收站，保留 `RECYCLE_RETENTION_DAYS = 7` 天（§7），期间可恢复，到期 purge 硬删除。WebUI 与 App 双入口。

**范围**: favorites, history, downloads, bookmarks, filters, quickSearches, downloadLabels（7 类实体）。**例外**: ehSession 墓碑不进回收站 UI，到期由 purge 静默清除（凭据类数据不留可恢复副本，§3.8/§4）。

**端点**（OpenAPI: `contracts/openapi.yaml`；schemas: `sync-schemas.json#/$defs/recycle*`）：

| 端点 | 语义 |
|---|---|
| `GET /api/v1/recycle/list?entity=&page=&pageSize=` | 分页列出回收站条目，返回 `RecycleListResponse{items: RecycleItem[], total, page, pageSize}`；`RecycleItem{entity, key, title, deletedAt, remainingDays}`，`entity` ∈ 7 类枚举，`deletedAt` = 墓碑行 `lastModified`（服务器打戳时刻），`remainingDays` = 距到期剩余天数 |
| `POST /api/v1/recycle/restore` | 批量恢复（body `RecycleRestoreRequest{items: [{entity, key}]}`）：行置 `deleted=false` 并由**服务器打新戳**（§1.3）= 一条普通 LWW 记录，按 §3.8/§6 传播到全部设备（其余设备的同键墓碑因戳更旧被压制）；返回 `RecycleRestoreResponse{restored, failed}` |
| `POST /api/v1/recycle/purge` | 硬删除到期墓碑；body `RecyclePurgeRequest{items?}` **缺省 = 清除全部到期条目**；返回 `RecyclePurgeResponse{purged}` |

**语义要点**:

- **恢复不是特判路径**：restore 只是写入一条 `deleted=false` 的普通记录并参与既有 LWW/传播机制（ADR-0006 D4：恢复 = LWW 记录）——全部设备经同一套 §3/§3.8 规则收敛，无需额外的「撤销删除」协议分支。
- **计时基准** = 墓碑行的 `lastModified`（服务器打戳时刻，§4.1 step 3）。
- **下载内容**: 下载实体条目 purge 时其文件移入 `dataDir/trash` 再保留 7 天后物理清除（ADR-0006 D5）；restore 在文件仍在 trash 保留期内可完整恢复文件引用。
- **ehSession 例外**: 登出墓碑不出现于 `/recycle/list`（凭据不留可恢复副本）；到期 purge 静默清除（§3.8 分类表）。
