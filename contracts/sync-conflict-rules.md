# AnotherViewer Sync Protocol — Conflict Resolution Rules

> Version: 2.0
> Date: 2026-08-03
> Schema: `contracts/sync-schemas.json` (draft 2020-12)
> ADR: `docs/adr/0003-three-party-model-sync-policy.md`
>
> v2.0 变更摘要（相对 v1.0）：引入可配置冲突策略 `conflictStrategy`（device_priority | lww | web_priority，默认 device_priority）；merge 全部参数化；A/C 下同键冲突无条件优先级胜；soft 实体删除传播按策略（§3.8）；tombstone 实体删除任何策略下传播；新增 §8 SyncPolicy 与 `/api/v1/sync/policy` 端点；§5.1 仲裁总序重写。v1.0 语义 = 策略 `lww`（B），完整保留为回退兜底。

## 1. Timestamp Format & Clock Skew

### 1.1 Timestamp Format

All timestamps in the sync protocol are **epoch milliseconds** (Unix timestamp × 1000), stored as JSON integers. Fields: `time`, `lastModified`, `timestamp`, `serverTimestamp`, `lastSeen`, `lastSyncTimestamp`.

### 1.2 Clock Skew Tolerance

Devices on a LAN may have unsynchronized clocks. The protocol tolerates skew as follows:

- **Tolerance window**: ±5 seconds (5000 ms). Two `lastModified` values within this window are considered **simultaneous**.
- **Arbiter by strategy**: Under `lww` (B), when timestamps are within the tolerance window the server's received-order breaks the tie (first push received wins for LWW entities; both are kept for union entities). Under `device_priority` (A) / `web_priority` (C) the platform priority order (§1.4) replaces received-order entirely — received order is never consulted.
- **NTP recommendation**: Server SHOULD run NTP. Android clients SHOULD use `System.currentTimeMillis()` (network-synced on most devices).
- **Monotonic guard**: A device MUST NOT send a `lastModified` older than its previous push for the same entity. Clients track a local high-water mark per entity.

### 1.3 lastModified 来源规则（客户端权威，服务器不打戳）

**规则**: 每个实体的 `lastModified` 都由**产生变更的客户端**维护并以高水位递增；服务端**不**用服务器时间戳覆盖客户端值，也不参与该值的生成。

- **服务器持久化原样保留**: 服务端把推送方携带的 `lastModified` 原样写入实体行（`entity.lastModified = dto.lastModified`），仅用于增量 pull 的 `since` 过滤与（策略 B 下的）LWW 仲裁。
- **唯一例外——删除墓碑的 bump**: history/bookmark 的 `deleted: true` push 会把行上 `lastModified` 提升为 `max(存量, 推送方)`（仍是客户端值的派生，不用服务器时钟）。
- **preferences 同样适用**: push 的 `lastModified` 由客户端维护；服务端以该值对照存量 `updatedAt`（±SKEW_TOLERANCE=5000ms）做 LWW 判定，客户端值不被服务器时间覆盖。preferences 不受 conflictStrategy 影响（单用户设置，后同步者即最终意图，见 ADR-0001）。
- **LWW 语义（仅策略 B）**: 双方各自维护的 `lastModified` 互相比较，差值超过 5000 ms 时新值胜出；差值在 ±5000 ms 内视为同时发生，落入 §5 的实体专属 tie-breaker。策略 A/C 下 `lastModified` **不参与同键仲裁**（§1.4），仅用于高水位/增量 pull/展示。

### 1.4 Conflict Strategy（conflictStrategy，v2 新增）

策略定义（`SyncPolicy.conflictStrategy`，§8；ADR-0003 D1）：

| 值 | 语义 | 平台序 |
|---|---|---|
| `device_priority` (A，**默认**) | 同键冲突 Android 胜 | android > web |
| `lww` (B) | v1.0 完整语义（时间戳 LWW + skew tie-break） | 无 |
| `web_priority` (C) | 同键冲突 Web 胜 | web > android |

- **平台判定**: `deviceId` 前缀（§7 `DEVICE_ID_FORMAT`，首个 `-` 之前）：`android-*` → android；`web-*`/`server-*` 等 → web 侧。
- **无条件优先**: A/C 下，同键（idempotency key）跨平台冲突**无条件**由优先端胜，不比较 `lastModified`。不为长期离线场景做保护设计（ADR-0003）；时间戳仅用于高水位/增量 pull/展示。
- **同平台同键**（如两个 android 设备）: 回退 B 语义（LWW + skew tie-break），平台序不适用。
- **切换语义**: 策略即时生效于其后的 merge；**不追溯**重合并已收敛数据（B→A 后需在权威端重做删除才能传播；UI tooltip 明示）。
- **策略权威**: Android App（ADR-0003 D2，§8）。

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

### 2.1 Idempotent Push

When the server receives a push, it upserts by idempotency key:

```
function upsertEntity(incoming, existingByKey, strategy):
    key = idempotencyKey(incoming)
    existing = existingByKey[key]
    if existing is null:
        store(incoming)
    else:
        merged = merge(existing, incoming, strategy)   // §3 / §3.8
        store(merged)
```

Retrying the same push with identical payloads is a no-op (the merge produces the same result).

## 3. Per-Entity Merge Strategies

所有 merge 函数签名含 `strategy`（§1.4）。**策略 B 下全部函数等价 v1.0 语义**（完整回退兜底）。删除 vs 保留的跨策略总规则见 §3.8；各实体节仅描述实体专属部分。

### 3.1 Favorites (LocalFavoriteInfo) — Union Merge

**Rule**: 存在性按 §3.8 union/删除规则；元数据（favoriteSlot、title 等）同键双活时按策略：B = LWW；A/C = 优先端记录无条件胜（同平台回退 LWW）。

```
function mergeFavorite(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)   // §3.8
    if r != null: return r
    // both alive
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)  // §3.8
    return lwwWithSkew(existing, incoming, tiebreak: firstReceivedWins)
```

**Behavior (B)**: If device A removes a favorite and device B still has it, the favorite survives. A "delete" only propagates when ALL devices have deleted the entry. **(A/C)**: 优先端删除无条件传播；非优先端删除在优先端持有该键时不传播（§3.8）。

### 3.2 History (HistoryInfo) — Last-Write-Wins + Tombstone

**Rule**: The most recent view wins (B). A/C 同键双活 = 优先端无条件胜。

```
function mergeHistory(existing, incoming, strategy):
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew (B only) → prefer the one with the later `time` (view time)
    if incoming.time > existing.time:
        return incoming
    return existing
```

**Delete behavior**: Tombstone（§3.8：任何策略下传播）。The server keeps a tombstone row instead of hard-deleting. A `deleted: true` push sets `deleted = true` and bumps `lastModified` on the stored row (or stores the tombstone as a new row if none exists), so incremental pulls (`since > 0`) can propagate the deletion to other devices. Clients delete their local row upon receiving `deleted: true`. Resurrection（双活 vs 墓碑）按 §3.8。

### 3.3 Downloads (DownloadInfo) — Union Merge + Status Sync

**Rule**: 存在性按 §3.8；mutable fields (`state`, `finished`, `downloaded`, `label`)：B = last-write-wins；A/C 双活 = 优先端记录胜。

```
function mergeDownload(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)
    if r != null: return r
    winner = (crossPlatform and strategy != B)
             ? priorityRecord(existing, incoming, strategy)
             : lwwWithSkew(existing, incoming, tiebreak: firstReceivedWins)

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

**Rule**: B = most recently updated page position wins; skew 内更高 page 胜。A/C 同键双活 = 优先端无条件胜。

```
function mergeBookmark(existing, incoming, strategy):
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew (B only) → prefer higher page number (further reading progress)
    if incoming.page > existing.page:
        return incoming
    return existing
```

**Delete behavior**: Tombstone（§3.8：任何策略下传播），bump 与增量 pull 同 §3.2。

### 3.5 Filters (Filter) — Union Merge

**Rule**: 存在性/enabled 按 §3.8 + 策略；B 的 skew tie-break 保留 additive bias。

```
function mergeFilter(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew (B only) → prefer enabled=true (additive bias)
    if incoming.enabled != existing.enabled:
        return incoming if incoming.enabled else existing
    return existing
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.6 Quick Searches (QuickSearch) — Union Merge

```
function mergeQuickSearch(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    return existing
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.7 Download Labels (DownloadLabel) — Union Merge

```
function mergeDownloadLabel(existing, incoming, strategy):
    r = mergeDeleteVsAlive(existing, incoming, strategy)
    if r != null: return r
    if crossPlatform(existing, incoming) and strategy != B:
        return priorityRecord(existing, incoming, strategy)
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    return existing
```

**Delete behavior**: Soft-delete，按 §3.8。

### 3.8 Strategy-Aware Delete/Resurrection & Priority Rules（v2 权威总规则）

实体分两类：**tombstone 实体**（history, bookmark）与 **soft 实体**（favorite, download, filter, quickSearch, downloadLabel）。

```
function crossPlatform(a, b): platformOf(a.deviceId) != platformOf(b.deviceId)
function priorityPlatform(strategy): A -> "android", C -> "web", B -> null
function priorityRecord(a, b, strategy):
    return a if platformOf(a.deviceId) == priorityPlatform(strategy) else b

function mergeDeleteVsAlive(existing, incoming, strategy):
    if existing.deleted == incoming.deleted: return null   // 同态 → 实体专属规则
    tomb = existing.deleted ? existing : incoming
    live = existing.deleted ? incoming : existing

    // tombstone 实体：删除任何策略下传播（显式用户意图，增量 pull 依赖）
    if isTombstoneEntity(entity):
        return tomb

    // soft 实体：
    if strategy == B:
        return live                       // v1 union：单边删除不传播
    prio = priorityPlatform(strategy)
    if platformOf(tomb.deviceId) == prio:
        return tomb                       // 优先端删除无条件传播
    if platformOf(live.deviceId) == prio:
        return live                       // 非优先端删除 vs 优先端持有 → 优先端胜（保留）
    return tomb                           // 双方均非优先端（罕见）→ 删除传播
```

**行为矩阵实现对照**（与 `docs/MASTER-2026-08-03.md` §4.2 一致，含 P0 细化）：

| 分歧场景 | A device_priority | B lww | C web_priority |
|---|---|---|---|
| 同键同时编辑（双活） | android 记录胜 | LWW+skew tie-break | web 记录胜 |
| 优先端删 / 非优先端保留 | 删除传播 | 保留 | 删除传播 |
| 非优先端删 / 优先端保留 | 保留 | 保留 | 保留 |
| 各删不同键·tombstone 实体 | 双向传播 | 双向传播 | 双向传播 |
| 各删不同键·soft 实体 | 优先端删传播；非优先端删在优先端持有时不传播、不持有时传播 | 不传播 | 同 A 镜像 |
| 不相交新增 | union | union | union |
| 非优先端删 / 优先端无该键 | 删除传播（无冲突对象） | — | 同 |

**Resurrection**（tomb 存量 vs live 推送）= 上表镜像：优先端 live 胜非优先端 tomb；非优先端 live vs 优先端 tomb → tomb 胜（soft）；tombstone 实体双活 vs tomb 按实体专属 LWW（新 live 复活，v1 同）。

## 4. Soft-Delete vs Hard-Delete Policy

| Entity | Delete Type | 策略依赖（v2） | Rationale |
|--------|------------|----------------|-----------|
| Favorite | **Soft** | §3.8 | Union merge — one device removing a favorite must not destroy another device's entry（B）；A/C 优先端删除可传播 |
| History | **Tombstone** | 无（恒传播） | Explicit user action should propagate; tombstone row retained for incremental pull |
| Download | **Soft** | §3.8 | One device clearing its list must not cancel another's downloads（B） |
| Bookmark | **Tombstone** | 无（恒传播） | Clearing reading position is an explicit user intent that should propagate |
| Filter | **Soft** | §3.8 | Additive bias — filters are safety/UX features |
| QuickSearch | **Soft** | §3.8 | User-created presets; union preserves all devices' presets（B） |
| DownloadLabel | **Soft** | §3.8 | Removing on one device shouldn't break another's organization（B） |

### 4.1 Soft-Delete Lifecycle

1. Device sets `deleted: true`, updates `lastModified`, pushes to server.
2. Server stores the tombstone record（merge 按 §3.8；A/C 下优先端删除覆盖非优先端活记录时同样落 `deleted=true` 行）。
3. On pull, other devices receive the tombstone and hide the entity from UI.
4. **Resurrection**: 按 §3.8 镜像规则；B 下任何设备 `deleted: false` + 更新 `lastModified` 即复活。
5. **Purge**: the server MAY purge tombstones where ALL known devices have pushed `deleted: true` and 30 days have elapsed since the last `lastModified`. Purge is optional and server-local.

### 4.2 Tombstone Lifecycle (History / Bookmark)

1. Device sets `deleted: true`, updates `lastModified`, pushes to server.
2. Server keeps the row as a tombstone: sets `deleted = true` and bumps `lastModified` (max of stored and incoming). If no row exists, the tombstone is stored as a new row.（任何策略。）
3. On incremental pull (`since > 0`), other devices receive the tombstone and delete their matching local records.
4. **Resurrection**: a later live push restores the row per §3.8 / §3.2 / §3.4.
5. **Purge**: the server MAY purge tombstones after 30 days (TOMBSTONE_PURGE_DAYS), as in §4.1.

## 5. Simultaneous Edit Arbitration

When two devices modify the same entity (same idempotency key) within the clock skew tolerance window (±5 s):

### 5.1 General Rule（v2 仲裁总序）

仲裁总序 = **① 策略序（A/C，跨平台同键，无条件） → ② 实体专属 tie-breaker（仅 B：history.time 晚者胜 / bookmark.page 高者胜 / filter.enabled additive bias） → ③ 服务器收到顺序（仅 B，skew 内 first-received-wins）**。

1. **策略序**: A → android 记录胜；C → web 记录胜。不比较时间戳、不看收到顺序。
2. **实体专属（B）**: History: later `time` wins; Bookmark: higher `page` wins; Filter: prefer `enabled=true`.
3. **收到顺序（B）**: The first push that arrives at the server is treated as the baseline.
4. **Union 实体存在性（B）**: Both records survive; additive bias for mutable bits.

### 5.2 Server Processing Order

The server MUST process pushes sequentially per idempotency key (serialize writes to the same key). Concurrent pushes to different keys MAY be processed in parallel.

```
// Server push handler (per entity)
lock(idempotencyKey):
    existing = store.get(key)
    merged = merge(existing, incoming, currentStrategy)   // §3 / §3.8
    store.put(key, merged)
```

### 5.3 Conflict Notification

The server does NOT notify clients of conflicts. The merge is deterministic and both devices will converge on the next pull. Clients SHOULD display the merged state without surfacing conflict UI.

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
- Each entity carries its own `lastModified` and `deviceId`.
- **v2**: `SyncPushRequest.policy`（可选）—android 平台 push 携带时为权威覆盖（§8 / D2）。

### 6.2 Pull (Server → Client)

```
GET /api/v1/sync/pull?since={lastSyncTimestamp}
Authorization: Bearer {api-token}

Response: SyncPullResponse (see sync-schemas.json)
```

- Server returns all entities with `lastModified > since`.
- Client merges received entities into local storage using the same merge rules（含 v2 strategy，取 `SyncPullResponse.policy`，缺省 `device_priority`；旧服务器无 policy 字段 → 客户端回退 B 且不报错）。
- Client updates its `lastSyncTimestamp` to `serverTimestamp` from the response.

### 6.3 Sync Cycle

A full sync cycle is: **push → pull → apply**.

```
function syncCycle():
    pushResult = POST /api/v1/sync/push { localChanges, deviceId, now(), policy? }
    pullResult = GET /api/v1/sync/pull?since={lastSyncTimestamp}
    strategy = pullResult.policy?.conflictStrategy ?? localFallback
    for each entity in pullResult.entities:
        localStore.merge(entity, strategy)   // using per-entity merge strategy
    lastSyncTimestamp = pullResult.serverTimestamp
```

## 7. Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `SKEW_TOLERANCE` | 5000 ms | Clock skew tolerance for simultaneous-edit detection |
| `TOMBSTONE_PURGE_DAYS` | 30 | Days after which all-deleted tombstones may be purged |
| `MAX_DEVICE_ID_LENGTH` | 128 chars | Maximum length of deviceId string |
| `DEVICE_ID_FORMAT` | `{platform}-{uuid}` | e.g. `android-550e8400-e29b-41d4-a716-446655440000` |
| `PRIORITY_ORDER` | android > web | 平台序常量（§1.4；A/C 仲裁唯一依据） |
| `STRATEGY_DEFAULT` | `device_priority` | conflictStrategy 缺省值 |
| `CLIENT_TIER_DEFAULT` | 1 | clientTier 缺省（同步+流式） |
| `AUTO_SYNC_INTERVAL_SEC_DEFAULT` | 900 | 自动同步间隔缺省；0=仅网络变化触发 |

## 8. SyncPolicy（v2 新增）

Schema: `sync-schemas.json#/$defs/syncPolicy` = `SyncPolicy{conflictStrategy, clientTier, autoSyncIntervalSec}`。

- **端点**: `GET /api/v1/sync/policy` 返回当前策略；`PUT /api/v1/sync/policy` 设置（任一已认证客户端）。OpenAPI: `contracts/openapi.yaml`。
- **pull 附 policy**: `SyncPullResponse.policy` 携带当前 SyncPolicy；客户端据此执行 merge（§6.2）。
- **push 附 policy（D2 权威）**: android 平台 push 的 `SyncPushRequest.policy` 服务端必须持久化（等价 PUT）；WebUI 的 PUT 允许但会被下一次 android push 覆盖，WebUI 高级面板须明示该语义。
- **clientTier**（D3）: 0=独立 / 1=同步+流式（默认） / 2=浏览代理 / 3=下载托管（押后）。档位由 App 选择并随 policy 声明；Tier-2/3 路由使用 `WebUiTier2ProxyInterceptor` 拦截器模式。
- **autoSyncIntervalSec**（D4）: 网络感知自动同步间隔；0=仅网络变化触发；新网络不自动配对。
- **兼容矩阵**: 旧 App×新服务器 → App 忽略 policy，服务器按 App 无 policy push 处理，收敛于 App 视图；新 App×旧服务器 → GET/PUT policy 404，App 回退 B，不报错；B = 完整回退兜底。
- **客户端义务**: 所有客户端 MUST 忽略 SyncPolicy/pull/push 中未知字段（前向兼容）。
