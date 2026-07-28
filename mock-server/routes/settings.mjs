// Settings get/put endpoints
import { Router } from 'express';

const router = Router();

// Mutable settings store
let settings = {
  download: {
    path: '/data/downloads',
    workerCount: 4,
    downloadDelay: 1000,
    downloadTimeout: 30000,
    maxConcurrentGalleries: 2,
    maxConcurrentImages: 8,
  },
  cache: {
    path: '/data/cache',
    sizeMb: 5120,
  },
  smb: {
    enabled: false,
  },
};

// GET /api/v1/settings
router.get('/', (req, res) => {
  res.json(settings);
});

// PUT /api/v1/settings
router.put('/', (req, res) => {
  const body = req.body || {};
  if (body.download) {
    settings.download = { ...settings.download, ...body.download };
  }
  if (body.cache) {
    settings.cache = { ...settings.cache, ...body.cache };
  }
  if (body.smb) {
    settings.smb = { ...settings.smb, ...body.smb };
  }
  res.json(true);
});

export default router;
