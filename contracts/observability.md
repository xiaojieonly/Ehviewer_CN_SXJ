# Observability Contract — Structured Logging, Metrics & Health

> **Status**: FROZEN (Wave 0, CA6)
> **Created**: 2026-07-28
> **Consumers**: B10 (health endpoint + metrics implementation), I2 (WS integration), I4 (performance validation), H4 (deployment hardening)
> **Dependencies**: None (Wave 0a, parallel with CA1–CA5)

---

## 1. Structured Logging

### 1.1 Format

- **Encoding**: JSON Lines (one JSON object per line, `\n` delimited)
- **Library**: `logstash-logback-encoder` (net.logstash.logback:logstash-logback-encoder:8.0+)
- **Fallback**: When JSON is disabled (`ehviewer.logging.json-enabled=false`), use standard Logback pattern console output for local development readability.

### 1.2 Base Fields (every log entry)

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `@timestamp` | string (ISO-8601) | Event time, UTC | `"2026-07-28T14:32:01.123Z"` |
| `level` | string | Log level | `"INFO"` |
| `logger` | string | Logger name (class FQN) | `"com.hippo.ehviewer.web.service.ImageStreamingService"` |
| `message` | string | Human-readable message | `"Image cache hit"` |
| `traceId` | string \| null | Request correlation ID (from MDC) | `"a1b2c3d4e5f6"` |
| `thread` | string | Thread name | `"http-nio-8080-exec-3"` |
| `app` | string | Application identifier | `"ehviewer-web"` |

**traceId propagation**:
- Generated per HTTP request via a servlet filter (UUID-based, 12-char hex prefix).
- Propagated to async/coroutine contexts via MDC + Kotlin coroutine context.
- WebSocket messages carry the traceId of the originating subscription.
- Background tasks (download, processing) generate their own traceId at task start.

### 1.3 Domain-Specific Log Events

Each domain event is logged via a structured key-value pattern. The `event` field identifies the event type; additional fields carry domain data.

#### `image.cache.hit` / `image.cache.miss`

Emitted on every image cache lookup. Level: **TRACE**.

```json
{
  "@timestamp": "2026-07-28T14:32:01.123Z",
  "level": "TRACE",
  "logger": "com.hippo.ehviewer.web.cache.ImageCacheService",
  "message": "Image cache hit",
  "traceId": "a1b2c3d4e5f6",
  "event": "image.cache.hit",
  "galleryId": 123456,
  "page": 3,
  "cacheLayer": "memory",
  "loadTimeMs": 2
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | `"image.cache.hit"` or `"image.cache.miss"` |
| `galleryId` | long | Gallery identifier |
| `page` | int | Page number (0-based) |
| `cacheLayer` | string | `"memory"` or `"disk"` (which layer was checked/hit) |
| `loadTimeMs` | long | Time to load from cache (0 for miss) |

On a miss, the log records the layer that was checked and `loadTimeMs: 0`:
```json
{
  "event": "image.cache.miss",
  "galleryId": 123456,
  "page": 3,
  "cacheLayer": "disk",
  "loadTimeMs": 0
}
```

#### `image.download`

Emitted when an image is fetched from the remote source. Level: **DEBUG** (success), **WARN** (retry), **ERROR** (final failure).

```json
{
  "@timestamp": "2026-07-28T14:32:02.456Z",
  "level": "DEBUG",
  "logger": "com.hippo.ehviewer.web.service.ImageStreamingService",
  "message": "Image downloaded",
  "traceId": "a1b2c3d4e5f6",
  "event": "image.download",
  "galleryId": 123456,
  "page": 3,
  "url": "https://ehgt.org/...",
  "durationMs": 340,
  "sizeBytes": 245760,
  "status": "success"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | `"image.download"` |
| `galleryId` | long | Gallery identifier |
| `page` | int | Page number |
| `url` | string | Source URL (may be truncated to host+path for privacy) |
| `durationMs` | long | Download duration |
| `sizeBytes` | long | Response body size |
| `status` | string | `"success"`, `"retry"`, `"failed"`, `"rate_limited"` (509) |

#### `image.process`

Emitted per image processing task step. Level: **DEBUG** (start/complete), **ERROR** (failure).

```json
{
  "@timestamp": "2026-07-28T14:33:00.789Z",
  "level": "DEBUG",
  "logger": "com.hippo.ehviewer.web.process.ImageProcessingService",
  "message": "Image processed",
  "traceId": "b2c3d4e5f6a1",
  "event": "image.process",
  "taskId": "proc-00042",
  "galleryId": 123456,
  "page": 3,
  "processor": "waifu2x",
  "durationMs": 4200,
  "status": "completed"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | `"image.process"` |
| `taskId` | string | Processing task identifier |
| `galleryId` | long | Gallery identifier |
| `page` | int | Page number |
| `processor` | string | Processor ID (`"waifu2x"`, `"noop"`) |
| `durationMs` | long | Processing duration |
| `status` | string | `"started"`, `"completed"`, `"failed"`, `"skipped"` |

#### `download.task`

Emitted on download task lifecycle transitions. Level: **INFO** (lifecycle), **WARN** (retry/stall), **ERROR** (failure).

```json
{
  "@timestamp": "2026-07-28T14:35:00.000Z",
  "level": "INFO",
  "logger": "com.hippo.ehviewer.web.service.DownloadService",
  "message": "Download task state changed",
  "traceId": "c3d4e5f6a1b2",
  "event": "download.task",
  "taskId": "dl-00123",
  "galleryId": 123456,
  "action": "state_change",
  "state": "downloading",
  "totalPages": 42,
  "downloadedPages": 17
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | `"download.task"` |
| `taskId` | string | Download task identifier |
| `galleryId` | long | Gallery identifier |
| `action` | string | `"created"`, `"state_change"`, `"progress"`, `"cancelled"` |
| `state` | string | `"pending"`, `"downloading"`, `"paused"`, `"completed"`, `"failed"` |
| `totalPages` | int | Total pages in gallery |
| `downloadedPages` | int | Pages downloaded so far |

#### `sync.operation`

Emitted per sync push/pull operation. Level: **INFO** (success), **ERROR** (failure).

```json
{
  "@timestamp": "2026-07-28T15:00:00.000Z",
  "level": "INFO",
  "logger": "com.hippo.ehviewer.web.sync.SyncService",
  "message": "Sync pull completed",
  "traceId": "d4e5f6a1b2c3",
  "event": "sync.operation",
  "deviceId": "android-a1b2c3",
  "direction": "pull",
  "entityCounts": {
    "favorites": 5,
    "history": 12,
    "downloads": 2,
    "progress": 8,
    "filters": 0
  },
  "durationMs": 120
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | `"sync.operation"` |
| `deviceId` | string | Client device identifier |
| `direction` | string | `"push"` or `"pull"` |
| `entityCounts` | object | Counts per entity type transferred |
| `durationMs` | long | Operation duration |

### 1.4 Log Level Policy

| Level | Usage |
|-------|-------|
| TRACE | Cache lookups (hit/miss), fine-grained diagnostics |
| DEBUG | Image downloads, processing steps, prefetch operations |
| INFO | Task lifecycle (download start/complete, sync operations, server startup) |
| WARN | Retries (download retry, rate-limit backoff), degraded states |
| ERROR | Final failures (download failed, processing failed, sync error) |

### 1.5 Logback Configuration

The JSON encoder is activated via a Spring profile or property:

```xml
<!-- logback-spring.xml (schematic) -->
<springProperty name="jsonEnabled" source="ehviewer.logging.json-enabled" defaultValue="true"/>

<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <!-- enabled when jsonEnabled=true -->
    <includeMdcKeyName>traceId</includeMdcKeyName>
    <customFields>{"app":"ehviewer-web"}</customFields>
  </encoder>
</appender>
```

When `ehviewer.logging.json-enabled=false`, switch to a pattern encoder:
```
%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

---

## 2. Health Endpoint

### 2.1 Endpoint

```
GET /api/health
```

- **Authentication**: None (public, for monitoring/load-balancer probes)
- **Response Content-Type**: `application/json`
- **HTTP Status**: `200` when status is `UP` or `DEGRADED`; `503` when `DOWN`

### 2.2 Response Schema

```json
{
  "status": "UP",
  "components": {
    "database": {
      "status": "UP",
      "details": {
        "type": "sqlite",
        "path": "/opt/ehviewer/data/ehviewer.db"
      }
    },
    "diskCache": {
      "status": "UP",
      "details": {
        "freeSpace": "42.5 GB",
        "freeSpaceBytes": 45634027520,
        "cacheUsedBytes": 3221225472,
        "cacheMaxBytes": 10737418240,
        "path": "/opt/ehviewer/cache"
      }
    },
    "ehentaiApi": {
      "status": "UP",
      "details": {
        "lastCheck": "2026-07-28T14:30:00Z",
        "responseTimeMs": 230,
        "loggedIn": true
      }
    },
    "waifu2x": {
      "status": "DOWN",
      "details": {
        "reason": "not configured",
        "url": null
      }
    }
  },
  "version": "1.0.0-SNAPSHOT",
  "uptime": "3d 14h 22m",
  "uptimeMs": 310920000,
  "timestamp": "2026-07-28T14:32:01Z"
}
```

### 2.3 Status Semantics

| Overall Status | Condition |
|----------------|-----------|
| `UP` | All required components UP (database, diskCache) |
| `DEGRADED` | Required components UP but optional components DOWN (e.g., waifu2x not configured, ehentaiApi unreachable) |
| `DOWN` | Any required component DOWN (database inaccessible, disk cache path unwritable) |

**Required components**: `database`, `diskCache`
**Optional components**: `ehentaiApi`, `waifu2x`

### 2.4 Component Health Checks

| Component | Check Method | UP Condition | DOWN Condition |
|-----------|-------------|--------------|----------------|
| `database` | Execute `SELECT 1` on SQLite | Query succeeds within 5s | Query fails or times out |
| `diskCache` | Check directory exists + writable + free space | Writable AND freeSpace > 100MB | Not writable OR freeSpace < 100MB |
| `ehentaiApi` | HTTP HEAD to `https://e-hentai.org` (cached, max every 60s) | Response 2xx/3xx within 10s | Timeout or connection refused |
| `waifu2x` | HTTP GET to configured URL `/health` (if configured) | Response 200 within 5s | Not configured, or unreachable |

### 2.5 Caching

Health checks are cached for **30 seconds** to avoid excessive probing. The `ehentaiApi` check is additionally rate-limited to once per 60 seconds.

---

## 3. Metrics Endpoint

### 3.1 Endpoint

```
GET /api/metrics
```

- **Authentication**: None (configurable, see §5)
- **Response Content-Type**: `application/json`
- **Implementation**: Backed by Micrometer `MeterRegistry` (already available via `spring-boot-starter-actuator`). Exposed as a custom controller that serializes selected meters into the schema below.

### 3.2 Response Schema

```json
{
  "timestamp": "2026-07-28T14:32:01Z",
  "metrics": {
    "ehviewer.cache.memory.entries": { "type": "gauge", "value": 142 },
    "ehviewer.cache.memory.size.bytes": { "type": "gauge", "value": 157286400 },
    "ehviewer.cache.disk.size.bytes": { "type": "gauge", "value": 3221225472 },
    "ehviewer.cache.disk.entries": { "type": "gauge", "value": 8432 },
    "ehviewer.cache.hit.ratio": { "type": "gauge", "value": 0.87 },
    "ehviewer.image.download.total": { "type": "counter", "value": 1523 },
    "ehviewer.image.download.duration.ms": {
      "type": "timer",
      "count": 1523,
      "totalMs": 487360,
      "meanMs": 320.0,
      "maxMs": 4200,
      "p50Ms": 280,
      "p95Ms": 890,
      "p99Ms": 2100
    },
    "ehviewer.image.prefetch.hit.ratio": { "type": "gauge", "value": 0.73 },
    "ehviewer.download.active": { "type": "gauge", "value": 2 },
    "ehviewer.download.completed.total": { "type": "counter", "value": 47 },
    "ehviewer.process.queue.size": { "type": "gauge", "value": 5 },
    "ehviewer.process.completed.total": { "type": "counter", "value": 312 },
    "ehviewer.process.duration.ms": {
      "type": "timer",
      "count": 312,
      "totalMs": 1310400,
      "meanMs": 4200.0,
      "maxMs": 12000,
      "p50Ms": 3800,
      "p95Ms": 8500,
      "p99Ms": 11000
    },
    "ehviewer.sync.push.total": { "type": "counter", "value": 23 },
    "ehviewer.sync.pull.total": { "type": "counter", "value": 45 },
    "ehviewer.ws.connections.active": { "type": "gauge", "value": 3 }
  }
}
```

### 3.3 Metric Definitions

| Metric Name | Type | Unit | Description | Source |
|-------------|------|------|-------------|--------|
| `ehviewer.cache.memory.entries` | gauge | count | Current entries in Caffeine memory cache | `ImageCacheService` |
| `ehviewer.cache.memory.size.bytes` | gauge | bytes | Estimated memory cache size | `ImageCacheService` |
| `ehviewer.cache.disk.size.bytes` | gauge | bytes | Total disk cache directory size | `ImageCacheService` (periodic scan) |
| `ehviewer.cache.disk.entries` | gauge | count | Number of files in disk cache | `ImageCacheService` (periodic scan) |
| `ehviewer.cache.hit.ratio` | gauge | 0–1 | Overall cache hit ratio (memory + disk hits / total lookups), rolling 5-min window | `ImageCacheService` |
| `ehviewer.image.download.total` | counter | count | Total image downloads attempted | `ImageStreamingService` |
| `ehviewer.image.download.duration.ms` | timer | ms | Download duration distribution | `ImageStreamingService` |
| `ehviewer.image.prefetch.hit.ratio` | gauge | 0–1 | **预读命中率**: prefetched pages that were subsequently requested before eviction / total prefetch operations, rolling 5-min window | `PrefetchService` |
| `ehviewer.download.active` | gauge | count | Currently active download tasks | `DownloadService` |
| `ehviewer.download.completed.total` | counter | count | Total download tasks completed successfully | `DownloadService` |
| `ehviewer.process.queue.size` | gauge | count | Pending + in-progress processing tasks | `ImageProcessingService` |
| `ehviewer.process.completed.total` | counter | count | Total processing tasks completed | `ImageProcessingService` |
| `ehviewer.process.duration.ms` | timer | ms | Processing duration distribution | `ImageProcessingService` |
| `ehviewer.sync.push.total` | counter | count | Total sync push operations | `SyncService` |
| `ehviewer.sync.pull.total` | counter | count | Total sync pull operations | `SyncService` |
| `ehviewer.ws.connections.active` | gauge | count | Active WebSocket connections | `WebSocketHandler` |

### 3.4 Prefetch Hit Ratio (Critical Performance Metric)

**Definition**: Of all pages that were prefetched (N+1 through N+K ahead of the current reading position), what fraction was actually requested by the reader before being evicted from cache.

**Calculation**:
```
prefetch.hit.ratio = prefetchHits / prefetchTotal  (rolling 5-min window)
```

Where:
- `prefetchTotal`: number of pages prefetched in the window
- `prefetchHits`: number of those prefetched pages that were subsequently served from cache (memory or disk) when the reader requested them

**Purpose**: Validates that the prefetch strategy (Phase 1.2, `ehviewer.reader.prefetch-pages`) is effective. A ratio < 0.5 suggests over-prefetching (wasting bandwidth) or under-prefetching (reader outpaces prefetch). Target: ≥ 0.7 under normal reading speed.

**Tags**: The underlying Micrometer counter supports tags for breakdown:
- `galleryId` (optional, high-cardinality — disabled by default, enable via config)

### 3.5 Timer/Histogram Details

Timer metrics (`ehviewer.image.download.duration.ms`, `ehviewer.process.duration.ms`) expose:

| Field | Description |
|-------|-------------|
| `count` | Total observations |
| `totalMs` | Sum of all durations |
| `meanMs` | Arithmetic mean |
| `maxMs` | Maximum observed (within decay window) |
| `p50Ms` | 50th percentile |
| `p95Ms` | 95th percentile |
| `p99Ms` | 99th percentile |

Percentiles computed via Micrometer's `Timer.builder().publishPercentiles(0.5, 0.95, 0.99)`.

---

## 4. Dashboard Data Endpoint

### 4.1 Endpoint

```
GET /api/metrics/dashboard
```

- **Authentication**: Same as `/api/metrics` (configurable)
- **Response Content-Type**: `application/json`
- **Purpose**: Pre-aggregated summary for a simple web dashboard (settings page or standalone monitoring view). Avoids requiring the frontend to compute derived values.

### 4.2 Response Schema

```json
{
  "timestamp": "2026-07-28T14:32:01Z",
  "summary": {
    "status": "UP",
    "uptime": "3d 14h 22m",
    "version": "1.0.0-SNAPSHOT"
  },
  "cache": {
    "memoryEntries": 142,
    "memoryUsedBytes": 157286400,
    "memoryMaxBytes": 209715200,
    "memoryUsagePercent": 75.0,
    "diskUsedBytes": 3221225472,
    "diskMaxBytes": 10737418240,
    "diskUsagePercent": 30.0,
    "diskEntries": 8432,
    "hitRatio": 0.87,
    "prefetchHitRatio": 0.73
  },
  "downloads": {
    "active": 2,
    "completedTotal": 47,
    "failedTotal": 3,
    "activeTasks": [
      {
        "taskId": "dl-00123",
        "galleryId": 123456,
        "galleryTitle": "...",
        "state": "downloading",
        "progress": 0.40,
        "downloadedPages": 17,
        "totalPages": 42,
        "speedBytesPerSec": 1048576
      }
    ]
  },
  "processing": {
    "queueSize": 5,
    "activeCount": 1,
    "completedTotal": 312,
    "failedTotal": 8,
    "processorAvailable": false,
    "processorId": null
  },
  "sync": {
    "pushTotal": 23,
    "pullTotal": 45,
    "lastSyncAt": "2026-07-28T15:00:00Z",
    "connectedDevices": 1
  },
  "websocket": {
    "activeConnections": 3
  },
  "recentErrors": [
    {
      "timestamp": "2026-07-28T14:28:00Z",
      "level": "ERROR",
      "event": "image.download",
      "message": "Download failed: 509 rate limited",
      "galleryId": 789012,
      "page": 15
    }
  ]
}
```

### 4.3 Field Descriptions

| Section | Field | Description |
|---------|-------|-------------|
| `summary` | `status` | Overall health (mirrors `/api/health`) |
| `cache` | `memoryUsagePercent` | `memoryUsedBytes / memoryMaxBytes * 100` |
| `cache` | `diskUsagePercent` | `diskUsedBytes / diskMaxBytes * 100` |
| `cache` | `hitRatio` | Rolling 5-min overall cache hit ratio |
| `cache` | `prefetchHitRatio` | Rolling 5-min prefetch hit ratio (预读命中率) |
| `downloads` | `activeTasks` | Array of currently running downloads (max 10 entries) |
| `downloads` | `speedBytesPerSec` | Current download speed per task (0 if paused) |
| `processing` | `processorAvailable` | Whether a non-noop processor is connected |
| `recentErrors` | — | Last 20 ERROR-level events (in-memory ring buffer, newest first) |

### 4.4 Recent Errors Buffer

- In-memory ring buffer of the last **20** ERROR-level log events.
- Each entry captures: `timestamp`, `level`, `event` (if domain event), `message`, and relevant IDs.
- No persistence — resets on restart.
- Excludes stack traces (available via logs, not dashboard).

---

## 5. Configuration Properties

### 5.1 Logging Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ehviewer.logging.json-enabled` | boolean | `true` | Enable JSON structured logging. Set `false` for human-readable console output during local development. |
| `ehviewer.logging.level.root` | string | `INFO` | Root logger level |
| `ehviewer.logging.level.web` | string | `INFO` | `com.hippo.ehviewer.web` package level |
| `ehviewer.logging.level.cache` | string | `TRACE` | Cache lookup logging (`ImageCacheService`). Set to `DEBUG` to suppress per-lookup TRACE in production. |
| `ehviewer.logging.level.download` | string | `DEBUG` | Download operation logging |
| `ehviewer.logging.level.process` | string | `DEBUG` | Image processing logging |
| `ehviewer.logging.level.sync` | string | `INFO` | Sync operation logging |

These map to Logback logger levels:
```yaml
ehviewer:
  logging:
    json-enabled: true
    level:
      root: INFO
      web: INFO
      cache: TRACE
      download: DEBUG
      process: DEBUG
      sync: INFO
```

### 5.2 Metrics Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ehviewer.metrics.enabled` | boolean | `true` | Enable metrics collection and endpoints |
| `ehviewer.metrics.endpoint` | string | `/api/metrics` | Base path for metrics endpoints |
| `ehviewer.metrics.dashboard.enabled` | boolean | `true` | Enable `/api/metrics/dashboard` endpoint |
| `ehviewer.metrics.prefetch.high-cardinality` | boolean | `false` | Enable per-gallery prefetch hit ratio tags (high cardinality, for debugging only) |
| `ehviewer.metrics.cache.disk-scan-interval` | duration | `60s` | How often to rescan disk cache size/entries (expensive I/O) |

```yaml
ehviewer:
  metrics:
    enabled: true
    endpoint: /api/metrics
    dashboard:
      enabled: true
    prefetch:
      high-cardinality: false
    cache:
      disk-scan-interval: 60s
```

### 5.3 Health Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ehviewer.health.cache-ttl` | duration | `30s` | Cache duration for health check results |
| `ehviewer.health.ehentai-check-interval` | duration | `60s` | Minimum interval between E-Hentai connectivity checks |
| `ehviewer.health.disk-min-free` | string | `100MB` | Minimum free disk space before diskCache reports DOWN |

---

## 6. Implementation Notes (for B10)

### 6.1 Dependencies

Already available in `build.gradle.kts`:
- `spring-boot-starter-actuator` (runtimeOnly → promote to `implementation` for Micrometer API access)
- `jackson-module-kotlin` (JSON serialization)

To add:
```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
implementation("io.micrometer:micrometer-core")  // via actuator BOM
```

### 6.2 Micrometer Registration Pattern

```kotlin
@Component
class ObservabilityMeters(registry: MeterRegistry) {
    // Gauges bound to service state
    val cacheMemoryEntries = registry.gauge("ehviewer.cache.memory.entries", AtomicInteger(0))
    val prefetchHits = registry.counter("ehviewer.image.prefetch.hits")
    val prefetchTotal = registry.counter("ehviewer.image.prefetch.total")
    val downloadTimer = Timer.builder("ehviewer.image.download.duration.ms")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
    // ...
}
```

### 6.3 TraceId Filter

```kotlin
@Component
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request, response, chain) {
        val traceId = request.getHeader("X-Trace-Id") ?: generateTraceId()
        MDC.put("traceId", traceId)
        response.setHeader("X-Trace-Id", traceId)
        try { chain.doFilter(request, response) }
        finally { MDC.remove("traceId") }
    }
}
```

### 6.4 Relationship to Spring Actuator

The custom `/api/health` and `/api/metrics` endpoints are **in addition to** Spring Actuator's `/actuator/health` and `/actuator/metrics`. Actuator endpoints remain available for infrastructure tooling (Prometheus scrape, Kubernetes probes) but the `/api/*` endpoints provide the EhViewer-specific schema defined here.

Actuator configuration:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

---

## 7. Verification Criteria (for I4, H4)

| Criterion | Method |
|-----------|--------|
| JSON logs parseable | Pipe stdout through `jq .` — every line valid JSON |
| All domain events present | Run download + read + sync flow; grep for each `event` value in logs |
| traceId consistent | Single request's logs share one traceId across async boundaries |
| Health endpoint correct | `curl /api/health` returns schema-conformant JSON; stop DB → returns 503 + DOWN |
| Metrics endpoint correct | `curl /api/metrics` returns all 16 metrics with correct types |
| Prefetch hit ratio works | Read 10 pages with prefetch K=3; assert `ehviewer.image.prefetch.hit.ratio` > 0 |
| Dashboard endpoint | `curl /api/metrics/dashboard` returns pre-aggregated summary |
| Performance gate | Prefetch hit ratio ≥ 0.7 under normal reading speed (I4 validation) |

---

## 8. Changelog

| Date | Version | Change |
|------|---------|--------|
| 2026-07-28 | 1.0 | Initial frozen specification |
