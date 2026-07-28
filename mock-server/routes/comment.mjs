// Comment endpoints
import { Router } from 'express';
import { getCommentsForGallery } from '../fixtures/comments.mjs';

const router = Router();

// GET /api/v1/comment/list/:gid
router.get('/list/:gid', (req, res) => {
  const gid = parseInt(req.params.gid, 10);
  res.json(getCommentsForGallery(gid));
});

// POST /api/v1/comment/post
router.post('/post', (req, res) => {
  res.json({ success: true });
});

// POST /api/v1/comment/vote
router.post('/vote', (req, res) => {
  res.json({ success: true });
});

export default router;
