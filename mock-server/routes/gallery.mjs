// Gallery search/detail/history/favorites/quick-search endpoints
import { Router } from 'express';
import { searchGalleries, getGalleryByGid, galleries } from '../fixtures/galleries.mjs';
import { localFavorites } from '../fixtures/favorites.mjs';
import { historyItems, quickSearches } from '../fixtures/history.mjs';

const router = Router();

// Mutable quick search store
let qsStore = [...quickSearches];
let nextQsId = 100;

// GET /api/v1/gallery/search
router.get('/search', (req, res) => {
  const keyword = req.query.keyword || '';
  const category = req.query.category ? parseInt(req.query.category, 10) : 0;
  const page = parseInt(req.query.page, 10) || 0;
  const pageSize = parseInt(req.query.pageSize, 10) || 20;
  res.json(searchGalleries({ keyword, category, page, pageSize }));
});

// GET /api/v1/gallery/history
router.get('/history', (req, res) => {
  const page = parseInt(req.query.page, 10) || 0;
  const pageSize = parseInt(req.query.pageSize, 10) || 20;
  const start = page * pageSize;
  const data = historyItems.slice(start, start + pageSize).map(h => {
    const g = galleries.find(g => g.gid === h.gid);
    return g || h;
  });
  res.json({ success: true, data, total: historyItems.length });
});

// POST /api/v1/gallery/history/:gid
router.post('/history/:gid', (req, res) => {
  res.json({ success: true });
});

// GET /api/v1/gallery/favorites
router.get('/favorites', (req, res) => {
  res.json({ success: true, data: localFavorites, total: localFavorites.length });
});

// GET /api/v1/gallery/quick-search
router.get('/quick-search', (req, res) => {
  res.json({ success: true, data: qsStore });
});

// POST /api/v1/gallery/quick-search
router.post('/quick-search', (req, res) => {
  const body = req.body || {};
  const newQs = {
    id: ++nextQsId,
    name: body.name || 'Untitled',
    mode: body.mode || 0,
    category: body.category || 0,
    keyword: body.keyword || null,
    advanceSearch: body.advanceSearch || 0,
    minRating: body.minRating || 0,
    pageFrom: body.pageFrom || 0,
    pageTo: body.pageTo || 0,
  };
  qsStore.push(newQs);
  res.json(newQs);
});

// DELETE /api/v1/gallery/quick-search/:id
router.delete('/quick-search/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  qsStore = qsStore.filter(q => q.id !== id);
  res.json({ success: true });
});

// GET /api/v1/gallery/:gid  (must be last - catch-all for numeric gid)
router.get('/:gid', (req, res) => {
  const gid = parseInt(req.params.gid, 10);
  const gallery = getGalleryByGid(gid);
  if (!gallery) {
    return res.status(404).json({ error: 'Gallery not found' });
  }
  res.json(gallery);
});

export default router;
