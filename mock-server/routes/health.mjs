// Health + metrics endpoints
import { Router } from 'express';

const router = Router();

const startTime = Date.now();

// GET /api/v1/health
router.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    components: {
      disk: { status: 'UP', details: '2.1 TB free of 4 TB' },
      database: { status: 'UP', details: 'SQLite connected' },
      network: { status: 'UP', details: 'E-Hentai reachable' },
      processor: { status: 'UP', details: 'waifu2x-mock available' },
      smb: { status: 'DOWN', details: 'Not configured' },
    },
  });
});

// GET /api/v1/metrics
router.get('/metrics', (req, res) => {
  const uptimeSeconds = Math.floor((Date.now() - startTime) / 1000);
  res.json({
    uptimeSeconds,
    jvmMemoryUsedBytes: 268435456,    // 256 MB
    jvmMemoryMaxBytes: 1073741824,    // 1 GB
    activeDownloads: 2,
    queuedProcessingTasks: 1,
    diskCacheUsedBytes: 1073741824,   // 1 GB
    diskCacheMaxBytes: 5368709120,    // 5 GB
    totalGalleriesServed: 15234,
  });
});

export default router;
