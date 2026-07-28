// Download CRUD + control endpoints
import { Router } from 'express';
import { downloads, labels, getDownloadsByLabel, getDownloadById } from '../fixtures/downloads.mjs';

const router = Router();

// Mutable download store
let dlStore = [...downloads];
let labelStore = [...labels];
let nextDlId = 2000;
let nextLabelId = 100;

// GET /api/v1/download/list
router.get('/list', (req, res) => {
  const label = req.query.label !== undefined ? parseInt(req.query.label, 10) : undefined;
  if (label !== undefined) {
    const filtered = dlStore.filter(d => d.label === label);
    return res.json({ downloads: filtered, labels: labelStore });
  }
  res.json({ downloads: dlStore, labels: labelStore });
});

// GET /api/v1/download/info/:id
router.get('/info/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  res.json(dl || null);
});

// POST /api/v1/download/add
router.post('/add', (req, res) => {
  const body = req.body || {};
  const newDl = {
    id: ++nextDlId,
    gid: body.gid || 0,
    token: body.token || '',
    title: body.title || 'Unknown Gallery',
    titleJpn: null,
    thumb: body.thumb || null,
    category: 256,
    state: 0,
    total: 50,
    done: 0,
    label: body.label || 0,
    downloadDir: null,
  };
  dlStore.push(newDl);
  res.json(true);
});

// POST /api/v1/download/start/:id
router.post('/start/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl && dl.state !== 3) {
    dl.state = 1;
    res.json(true);
  } else {
    res.json(false);
  }
});

// POST /api/v1/download/start-all
router.post('/start-all', (req, res) => {
  dlStore.forEach(d => {
    if (d.state === 0 || d.state === 2) d.state = 1;
  });
  res.json(true);
});

// POST /api/v1/download/pause/:id
router.post('/pause/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl && dl.state === 1) {
    dl.state = 2;
    res.json(true);
  } else {
    res.json(false);
  }
});

// POST /api/v1/download/cancel/:id
router.post('/cancel/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl) {
    dl.state = 0;
    dl.done = 0;
    res.json(true);
  } else {
    res.json(false);
  }
});

// DELETE /api/v1/download/delete/:id
router.delete('/delete/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = dlStore.findIndex(d => d.id === id);
  if (idx >= 0) {
    dlStore.splice(idx, 1);
    res.json(true);
  } else {
    res.json(false);
  }
});

// POST /api/v1/download/label
router.post('/label', (req, res) => {
  const body = req.body || {};
  labelStore.push({ id: ++nextLabelId, label: body.label || 'New Label', time: Date.now() });
  res.json(true);
});

// DELETE /api/v1/download/label/:id
router.delete('/label/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  labelStore = labelStore.filter(l => l.id !== id);
  res.json(true);
});

export default router;
