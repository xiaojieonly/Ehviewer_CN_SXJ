// Image streaming endpoints - placeholder SVG images
import { Router } from 'express';
import { CATEGORY_COLORS } from '../fixtures/galleries.mjs';

const router = Router();

function generatePlaceholderSvg(galleryId, page, width, category) {
  const color = CATEGORY_COLORS[category] || '#607d8b';
  const w = width || 800;
  const h = Math.round(w * 1.414); // A4-ish aspect ratio
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <rect width="${w}" height="${h}" fill="${color}" opacity="0.15"/>
  <rect x="2" y="2" width="${w - 4}" height="${h - 4}" fill="none" stroke="${color}" stroke-width="2" rx="8"/>
  <text x="${w / 2}" y="${h / 2 - 40}" text-anchor="middle" font-family="Arial, sans-serif" font-size="${Math.round(w / 12)}" fill="${color}" font-weight="bold">Gallery ${galleryId}</text>
  <text x="${w / 2}" y="${h / 2 + 20}" text-anchor="middle" font-family="Arial, sans-serif" font-size="${Math.round(w / 8)}" fill="${color}">Page ${page}</text>
  <text x="${w / 2}" y="${h / 2 + 70}" text-anchor="middle" font-family="Arial, sans-serif" font-size="${Math.round(w / 20)}" fill="${color}" opacity="0.7">${w} x ${h} px</text>
  <text x="${w / 2}" y="${h - 30}" text-anchor="middle" font-family="Arial, sans-serif" font-size="${Math.round(w / 25)}" fill="${color}" opacity="0.5">AnotherViewer Mock Server</text>
</svg>`;
}

// GET /api/v1/image/proxy
router.get('/proxy', (req, res) => {
  const url = req.query.url || 'unknown';
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="566">
  <rect width="400" height="566" fill="#9e9e9e" opacity="0.2"/>
  <text x="200" y="270" text-anchor="middle" font-family="Arial" font-size="16" fill="#666">Proxied Image</text>
  <text x="200" y="300" text-anchor="middle" font-family="Arial" font-size="10" fill="#999">${url.slice(0, 50)}</text>
</svg>`;
  res.setHeader('Content-Type', 'image/svg+xml');
  res.send(svg);
});

// GET /api/v1/image/mock-thumb.svg — placeholder thumbnail served to
// fixtures pointing at a local URL (must precede the :galleryId/:page catch-all)
router.get('/mock-thumb.svg', (req, res) => {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="250" height="350" viewBox="0 0 250 350">
  <rect width="250" height="350" fill="#607d8b" opacity="0.15"/>
  <rect x="3" y="3" width="244" height="344" fill="none" stroke="#607d8b" stroke-width="2" rx="10"/>
  <text x="125" y="168" text-anchor="middle" font-family="Arial, sans-serif" font-size="18" fill="#607d8b" font-weight="bold">Mock Thumb</text>
  <text x="125" y="195" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" fill="#607d8b" opacity="0.7">250 x 350 px</text>
</svg>`;
  res.setHeader('Content-Type', 'image/svg+xml');
  res.setHeader('Cache-Control', 'public, max-age=86400');
  res.send(svg);
});

// GET /api/v1/image/cache/status
router.get('/cache/status', (req, res) => {
  res.json({ cacheSize: 52428800 }); // 50MB
});

// POST /api/v1/image/cache/clear
router.post('/cache/clear', (req, res) => {
  res.json({ success: true });
});

// GET /api/v1/image/:galleryId/:page
router.get('/:galleryId/:page', (req, res) => {
  const galleryId = parseInt(req.params.galleryId, 10);
  const page = parseInt(req.params.page, 10);
  const w = req.query.w ? parseInt(req.query.w, 10) : undefined;
  const enhanced = req.query.enhanced === 'true';

  // Use galleryId to pick a category color (deterministic)
  const categoryKeys = Object.keys(CATEGORY_COLORS).map(Number);
  const category = categoryKeys[galleryId % categoryKeys.length];

  const svg = generatePlaceholderSvg(galleryId, page, w, category);
  res.setHeader('Content-Type', 'image/svg+xml');
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Cache-Control', 'public, max-age=3600');
  if (enhanced) {
    res.setHeader('X-Enhanced', 'true');
  }
  res.send(svg);
});

export default router;
