# AnotherViewer Sync Protocol — Conflict Resolution Rules

> Version: 1.0
> Date: 2026-07-28
> Schema: `contracts/sync-schemas.json` (draft 2020-12)

## 1. Timestamp Format & Clock Skew

### 1.1 Timestamp Format

All timestamps in the sync protocol are **epoch milliseconds** (Unix timestamp × 1000), stored as JSON integers. Fields: `time`, `lastModified`, `timestamp`, `serverTimestamp`, `lastSeen`, `lastSyncTimestamp`.

### 1.2 Clock Skew Tolerance

Devices on a LAN may have unsynchronized clocks. The protocol tolerates skew as follows:

- **Tolerance window**: ±5 seconds (5000 ms). Two `lastModified` values within this window are considered **simultaneous**.
- **Server as arbiter**: When timestamps are within the tolerance window, the server's received-order breaks the tie (first push received wins for LWW entities; both are kept for union entities).
- **NTP recommendation**: Server SHOULD run NTP. Android clients SHOULD use `System.currentTimeMillis()` (network-synced on most devices).
- **Monotonic guard**: A device MUST NOT send a `lastModified` older than its previous push for the same entity. Clients track a local high-water mark per entity.

### 1.3 lastModified 来源规则（客户端权威，服务器不打戳）

**规则**: 每个实体的 `lastModified` 都由**产生变更的客户端**维护并以高水位递增；服务端**不**用服务器时间戳覆盖客户端值，也不参与该值的生成。

- **服务器持久化原样保留**: 服务端把推送方携带的 `lastModified` 原样写入实体行（`entity.lastModified = dto.lastModified`），仅用于增量 pull 的 `since` 过滤与 LWW 仲裁。
- **唯一例外——删除墓碑的 bump**: history/bookmark 的 `deleted: true` push 会把行上 `lastModified` 提升为 `max(存量, 推送方)`（仍是客户端值的派生，不用服务器时钟）。
- **preferences 同样适用**: push 的 `lastModified` 由客户端维护；服务端以该值对照存量 `updatedAt`（±SKEW_TOLERANCE=5000ms）做 LWW 判定，客户端值不被服务器时间覆盖。
- **LWW 语义**: 双方各自维护的 `lastModified` 互相比较，差值超过 5000 ms 时新值胜出；差值在 ±5000 ms 内视为同时发生，落入 §5 的实体专属 tie-breaker。该语义对所有实体统一（收藏/下载/过滤等 union 实体在 LWW 之上叠加 union/复活规则，见 §3）。

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
function upsertEntity(incoming, existingByKey):
    key = idempotencyKey(incoming)
    existing = existingByKey[key]
    if existing is null:
        store(incoming)
    else:
        merged = merge(existing, incoming)   // strategy per §3
        store(merged)
```

Retrying the same push with identical payloads is a no-op (the merge produces the same result).

## 3. Per-Entity Merge Strategies

### 3.1 Favorites (LocalFavoriteInfo) — Union Merge

**Rule**: Never delete a remote entry. The union of all devices' favorites is the truth.

```
function mergeFavorite(existing, incoming):
    // Both records exist → keep the one with the later lastModified
    // but NEVER remove an entry just because one device deleted it
    if incoming.deleted and not existing.deleted:
        // Device deleted locally, but another device still has it → keep alive
        return existing   // ignore the soft-delete
    if existing.deleted and not incoming.deleted:
        return incoming   // resurrect
    // Both alive or both deleted → last-write-wins on metadata
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew window → server keeps existing (first-received wins)
    return existing
```

**Behavior**: If device A removes a favorite and device B still has it, the favorite survives. A "delete" only propagates when ALL devices have deleted the entry (all pushed `deleted: true`).

### 3.2 History (HistoryInfo) — Last-Write-Wins

**Rule**: The most recent view wins. History is a single record per gallery, updated each time the gallery is opened.

```
function mergeHistory(existing, incoming):
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew → prefer the one with the later `time` (view time)
    if incoming.time > existing.time:
        return incoming
    return existing
```

**Delete behavior**: The server keeps a tombstone row instead of hard-deleting. A `deleted: true` push sets `deleted = true` and bumps `lastModified` on the stored row (or stores the tombstone as a new row if none exists), so incremental pulls (`since > 0`) can propagate the deletion to other devices. Clients delete their local row upon receiving `deleted: true`.

### 3.3 Downloads (DownloadInfo) — Union Merge + Status Sync

**Rule**: Union merge for existence; last-write-wins for mutable fields (`state`, `finished`, `downloaded`, `label`).

```
function mergeDownload(existing, incoming):
    // Existence: union — a download entry is never removed by one device
    if incoming.deleted and not existing.deleted:
        return existing   // other device still tracks this download
    if existing.deleted and not incoming.deleted:
        return incoming

    // Mutable state fields: last-write-wins
    winner = (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE)
             ? incoming : existing

    // Immutable identity fields: keep from whichever record has them populated
    result = clone(winner)
    if result.archiveUri is null and other.archiveUri is not null:
        result.archiveUri = other.archiveUri

    return result
```

**Status sync specifics**:
- `state` transitions are NOT validated server-side. The latest `lastModified` wins.
- A download in `STATE_DOWNLOAD` (2) on one device and `STATE_FINISH` (3) on another resolves to whichever was written later.
- `finished`, `total` are progress counters that follow the winner.
- `downloaded` is **NOT a sync field**: it is a session-scoped counter (pages fetched during the current session on the sending device, not bytes). The server has no `downloaded` column and never persists it, and a pulled value is never merged or stored — the wire always carries the sending device's current-session value. Devices must not rely on it across devices; use `finished`/`total` for durable progress.
- `label` is transmitted as the label **name** (string) and mapped to/from the server-side `download_label.id` at push/pull; a label name without a server row is auto-created on push (mirroring `DownloadService.createLabel`), so a download never silently loses its label.

### 3.4 Bookmarks / Reading Progress (BookmarkInfo) — Last-Write-Wins

**Rule**: The most recently updated page position wins.

```
function mergeBookmark(existing, incoming):
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew → prefer higher page number (further reading progress)
    if incoming.page > existing.page:
        return incoming
    return existing
```

**Delete behavior**: The server keeps a tombstone row instead of hard-deleting. A `deleted: true` push sets `deleted = true` and bumps `lastModified` on the stored row (or stores the tombstone as a new row if none exists), so incremental pulls (`since > 0`) can propagate the deletion to other devices. Clients delete their local row upon receiving `deleted: true`. Removing a bookmark on one device still removes it everywhere.

### 3.5 Filters (Filter) — Union Merge

**Rule**: Union of all filter rules. A filter disabled on one device remains available for others.

```
function mergeFilter(existing, incoming):
    // Idempotency key: (mode, text)
    // If one device disables and another enables → latest lastModified wins for `enabled`
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    // Within skew → prefer enabled=true (additive bias)
    if incoming.enabled != existing.enabled:
        return incoming if incoming.enabled else existing
    return existing
```

**Delete behavior**: Filters use soft-delete. A `deleted: true` filter is hidden from the active list but remains in the sync store. If another device still has it active (`deleted: false`), the active version wins (same union logic as favorites).

### 3.6 Quick Searches (QuickSearch) — Union Merge

**Rule**: Union of all saved search presets, keyed by `name`.

```
function mergeQuickSearch(existing, incoming):
    if incoming.deleted and not existing.deleted:
        return existing
    if existing.deleted and not incoming.deleted:
        return incoming
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    if existing.lastModified > incoming.lastModified + SKEW_TOLERANCE:
        return existing
    return existing
```

**Delete behavior**: Soft-delete. Same union semantics as favorites.

### 3.7 Download Labels (DownloadLabel) — Union Merge

**Rule**: Union of all label names.

```
function mergeDownloadLabel(existing, incoming):
    if incoming.deleted and not existing.deleted:
        return existing
    if existing.deleted and not incoming.deleted:
        return incoming
    if incoming.lastModified > existing.lastModified + SKEW_TOLERANCE:
        return incoming
    return existing
```

**Delete behavior**: Soft-delete. A label deleted on one device persists if another device still uses it.

## 4. Soft-Delete vs Hard-Delete Policy

| Entity | Delete Type | Rationale |
|--------|------------|-----------|
| Favorite | **Soft** | Union merge — one device removing a favorite must not destroy another device's entry |
| History | **Tombstone** | Ephemeral data; clearing history is an explicit user action that should propagate. The row is retained as a tombstone (`deleted=true` + bumped `lastModified`) so incremental pull can deliver the deletion; a newer live view resurrects it via LWW |
| Download | **Soft** | Download records represent server-side tasks; one device clearing its list must not cancel another's downloads |
| Bookmark | **Tombstone** | Clearing reading position is an explicit user intent that should propagate. The row is retained as a tombstone (`deleted=true` + bumped `lastModified`) so incremental pull can deliver the deletion; a newer live page position resurrects it via LWW |
| Filter | **Soft** | Additive bias — filters are safety/UX features; removing on one device shouldn't remove for all |
| QuickSearch | **Soft** | User-created presets; union preserves all devices' presets |
| DownloadLabel | **Soft** | Labels organize downloads; removing on one device shouldn't break another's organization |

### 4.1 Soft-Delete Lifecycle

1. Device sets `deleted: true`, updates `lastModified`, pushes to server.
2. Server stores the tombstone record.
3. On pull, other devices receive the tombstone and hide the entity from UI.
4. **Resurrection**: If any device pushes the same idempotency key with `deleted: false` and a later `lastModified`, the record is resurrected.
5. **Purge**: The server MAY purge tombstones where ALL known devices have pushed `deleted: true` and 30 days have elapsed since the last `lastModified`. Purge is optional and server-local.

### 4.2 Tombstone Lifecycle (History / Bookmark)

1. Device sets `deleted: true`, updates `lastModified`, pushes to server.
2. Server keeps the row as a tombstone: sets `deleted = true` and bumps `lastModified` (max of stored and incoming). If no row exists, the tombstone is stored as a new row.
3. On incremental pull (`since > 0`), other devices receive the tombstone (its `lastModified` was bumped past the high-water mark) and delete their matching local records.
4. **Resurrection**: a later live push (newer `lastModified`) restores the row via last-write-wins (§3.2 / §3.4).
5. **Purge**: the server MAY purge tombstones after 30 days (TOMBSTONE_PURGE_DAYS), as in §4.1.

## 5. Simultaneous Edit Arbitration

When two devices modify the same entity (same idempotency key) within the clock skew tolerance window (±5 s):

### 5.1 General Rule

1. **Server receive order**: The first push that arrives at the server is treated as the baseline. The second push is merged against it.
2. **For LWW entities** (History, Bookmark): If `lastModified` values are within ±5 s, the tie-breaker is entity-specific:
   - History: later `time` (view timestamp) wins.
   - Bookmark: higher `page` number wins (further progress).
3. **For union entities** (Favorite, Download, Filter, QuickSearch, DownloadLabel): Both records survive. The `enabled` / mutable fields use an additive bias (prefer `enabled: true`, prefer non-null values).

### 5.2 Server Processing Order

The server MUST process pushes sequentially per idempotency key (serialize writes to the same key). Concurrent pushes to different keys MAY be processed in parallel.

```
// Server push handler (per entity)
lock(idempotencyKey):
    existing = store.get(key)
    merged = merge(existing, incoming)
    store.put(key, merged)
```

### 5.3 Conflict Notification

The server does NOT notify clients of conflicts. The merge is deterministic and both devices will converge on the next pull. Clients SHOULD display the merged state without surfacing conflict UI.

## 6. Incremental Sync Protocol

### 6.1 Push (Client → Server)

```
POST /api/sync/push
Content-Type: application/json
Authorization: Bearer {api-token}

Body: SyncPushRequest (see sync-schemas.json)
```

- Client sends ALL entities modified since its last successful sync.
- Client tracks `lastSyncTimestamp` locally (persisted in SharedPreferences / Room).
- Each entity carries its own `lastModified` and `deviceId`.

### 6.2 Pull (Server → Client)

```
GET /api/sync/pull?since={lastSyncTimestamp}
Authorization: Bearer {api-token}

Response: SyncPullResponse (see sync-schemas.json)
```

- Server returns all entities with `lastModified > since`.
- Client merges received entities into local storage using the same merge rules.
- Client updates its `lastSyncTimestamp` to `serverTimestamp` from the response.

### 6.3 Sync Cycle

A full sync cycle is: **push → pull → apply**.

```
function syncCycle():
    pushResult = POST /api/sync/push { localChanges, deviceId, now() }
    pullResult = GET /api/sync/pull?since={lastSyncTimestamp}
    for each entity in pullResult.entities:
        localStore.merge(entity)   // using per-entity merge strategy
    lastSyncTimestamp = pullResult.serverTimestamp
```

## 7. Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `SKEW_TOLERANCE` | 5000 ms | Clock skew tolerance for simultaneous-edit detection |
| `TOMBSTONE_PURGE_DAYS` | 30 | Days after which all-deleted tombstones may be purged |
| `MAX_DEVICE_ID_LENGTH` | 128 chars | Maximum length of deviceId string |
| `DEVICE_ID_FORMAT` | `{platform}-{uuid}` | e.g. `android-550e8400-e29b-41d4-a716-446655440000` |
