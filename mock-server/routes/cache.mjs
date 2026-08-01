// Cache stats/management endpoints
import { Router } from 'express';

const router = Router();

// GET /api/v1/cache/stats
router.get('/stats', (req, res) => {
  res.json({
    diskCacheSizeBytes: 1073741824,     // 1 GB
    diskCacheMaxBytes: 5368709120,      // 5 GB
    memoryCacheEntries: 256,
    memoryCacheMaxEntries: 512,
    hitCount: 15234,
    missCount: 3421,
    hitRate: 0.817,
  });
});

// DELETE /api/v1/cache/gallery/:id
router.delete('/gallery/:id', (req, res) => {
  res.json({ success: true });
});

// NOTE: the stale POST /api/v1/cache/clear was removed — the real backend
// only exposes POST /api/v1/image/cache/clear (see routes/image.mjs), which
// the frontend AdminServer already calls.
export default router;
