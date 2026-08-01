// Health + metrics endpoints
import { Router } from 'express';

const router = Router();

const VERSION = '1.0.0-SNAPSHOT';
const startTime = Date.now();

function formatUptime(ms) {
  const seconds = Math.floor(ms / 1000);
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h ${minutes}m`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

// GET /api/v1/health
// Shape mirrors HealthController: top-level status/version/uptime/uptimeMs/
// timestamp + components {database, diskCache, ehentaiApi} with Map details.
// Defaults to 200; pass ?simulate=down to exercise the real 503 path.
router.get('/health', (req, res) => {
  const uptimeMs = Date.now() - startTime;
  const components = {
    database: {
      status: 'UP',
      details: { type: 'sqlite' },
    },
    diskCache: {
      status: 'UP',
      details: {
        freeSpace: '495.0 GB',
        freeSpaceBytes: '531502202880',
        path: '/tmp/ehviewer-cache',
      },
    },
    ehentaiApi: {
      status: 'UP',
      details: {
        lastCheck: new Date(Date.now() - 60000).toISOString(),
        responseTimeMs: '482',
        cached: 'true',
      },
    },
  };
  if (req.query.simulate === 'down') {
    components.database.status = 'DOWN';
    components.database.details = { reason: 'simulated failure' };
  }

  const requiredUp = components.database.status === 'UP' &&
    components.diskCache.status === 'UP';
  const optionalDown = Object.values(components).some(c => c.status === 'DOWN');
  const overallStatus = !requiredUp ? 'DOWN' : optionalDown ? 'DEGRADED' : 'UP';

  res.status(overallStatus === 'DOWN' ? 503 : 200).json({
    status: overallStatus,
    components,
    version: VERSION,
    uptime: formatUptime(uptimeMs),
    uptimeMs,
    timestamp: new Date().toISOString(),
  });
});

// GET /api/v1/metrics
// Flat shape mirrors MetricsV1Response (timestamp, uptimeSeconds, JVM memory,
// downloads, processing queue, cache usage, galleries served) plus the
// retained legacy `metrics` map.
router.get('/metrics', (req, res) => {
  const uptimeSeconds = Math.floor((Date.now() - startTime) / 1000);
  res.json({
    timestamp: new Date().toISOString(),
    uptimeSeconds,
    jvmMemoryUsedBytes: 268435456,    // 256 MB
    jvmMemoryMaxBytes: 1073741824,    // 1 GB
    activeDownloads: 2,
    queuedProcessingTasks: 1,
    diskCacheUsedBytes: 1073741824,   // 1 GB
    diskCacheMaxBytes: 5368709120,    // 5 GB
    totalGalleriesServed: 15234,
    metrics: {
      'ehviewer.cache.memory.entries': { type: 'gauge', value: 256 },
      'ehviewer.cache.memory.size.bytes': { type: 'gauge', value: 0 },
      'ehviewer.cache.disk.size.bytes': { type: 'gauge', value: 1073741824 },
      'ehviewer.cache.disk.entries': { type: 'gauge', value: 2048 },
      'ehviewer.cache.hit.ratio': { type: 'gauge', value: 0.817 },
      'ehviewer.download.active': { type: 'gauge', value: 2 },
      'ehviewer.download.completed.total': { type: 'counter', value: 1240 },
      'ehviewer.process.queue.size': { type: 'gauge', value: 1 },
      'ehviewer.process.completed.total': { type: 'counter', value: 0 },
      'ehviewer.ws.connections.active': { type: 'gauge', value: 0 },
    },
  });
});

export default router;
