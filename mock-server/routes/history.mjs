// Dedicated history controller endpoints
import { Router } from 'express';
import { historyItems } from '../fixtures/history.mjs';

const router = Router();

// Mutable history store
let historyStore = [...historyItems];

// GET /api/v1/history/list
router.get('/list', (req, res) => {
  res.json({ history: historyStore });
});

// DELETE /api/v1/history/clear
router.delete('/clear', (req, res) => {
  historyStore = [];
  res.json({ success: true });
});

export default router;
