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
  security: {
    requireAuth: false,
    sessionTimeout: 86400,
  },
  processing: {
    enabled: false,
    defaultType: 'UPSCALE_2X',
    outputFormat: 'png',
    outputQuality: 90,
  },
  proxy: {
    enabled: false,
    type: 'http',
    host: '',
    port: 0,
    username: '',
    password: '',
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
  if (body.security) {
    settings.security = { ...settings.security, ...body.security };
  }
  if (body.processing) {
    settings.processing = { ...settings.processing, ...body.processing };
  }
  if (body.proxy) {
    settings.proxy = { ...settings.proxy, ...body.proxy };
  }
  res.json(true);
});

export default router;
