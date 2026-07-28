// Remote favorites management endpoints
import { Router } from 'express';
import { getFavoritesBySlot } from '../fixtures/favorites.mjs';

const router = Router();

// GET /api/v1/favorite/list
router.get('/list', (req, res) => {
  const slot = parseInt(req.query.slot, 10) || 0;
  const page = parseInt(req.query.page, 10) || 1;
  res.json(getFavoritesBySlot(slot, page));
});

// POST /api/v1/favorite/add
router.post('/add', (req, res) => {
  res.json({ success: true });
});

// DELETE /api/v1/favorite/remove
router.delete('/remove', (req, res) => {
  res.json({ success: true });
});

export default router;
