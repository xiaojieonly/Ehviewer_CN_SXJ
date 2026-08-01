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
// Real backend (DownloadService.listDownloads): label null or 0 means "all".
router.get('/list', (req, res) => {
  const label = req.query.label !== undefined ? parseInt(req.query.label, 10) : undefined;
  const filtered = (label !== undefined && label !== 0)
    ? dlStore.filter(d => d.label === label)
    : dlStore;
  res.json({ downloads: filtered, labels: labelStore });
});

// GET /api/v1/download/info/:id
router.get('/info/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  res.json(dl || null);
});

// POST /api/v1/download/add
// Real backend (DownloadService.addDownload): dedupes by gid, category 0,
// state 0, total/done 0, downloadDir = download.path/<gid>.
router.post('/add', (req, res) => {
  const body = req.body || {};
  if (dlStore.some(d => d.gid === (body.gid || 0))) return res.json(false);
  const newDl = {
    id: ++nextDlId,
    gid: body.gid || 0,
    token: body.token || '',
    title: body.title ?? null,
    titleJpn: '',
    thumb: body.thumb ?? null,
    category: 0,
    state: 0,
    total: 0,
    done: 0,
    label: body.label || 0,
    downloadDir: `/downloads/${body.gid || 0}`,
    error: null,
  };
  dlStore.push(newDl);
  res.json(true);
});

// POST /api/v1/download/start/:id
// Real backend (DownloadService.startDownload): refuses only WAIT(1)/DOWNLOADING(2);
// finished(3), failed(4) and idle(0) rows may be (re)started.
router.post('/start/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl && dl.state !== 1 && dl.state !== 2) {
    dl.state = 1;
    dl.error = null;
    res.json(true);
  } else {
    res.json(false);
  }
});

// POST /api/v1/download/start-all
// Real backend (DownloadService.startAllDownloads): only idle (0) rows.
router.post('/start-all', (req, res) => {
  dlStore.forEach(d => {
    if (d.state === 0) {
      d.state = 1;
      d.error = null;
    }
  });
  res.json(true);
});

// POST /api/v1/download/pause/:id
// Real backend (DownloadService.pauseDownload): refuses only idle (0).
router.post('/pause/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl && dl.state !== 0) {
    dl.state = 0;
    res.json(true);
  } else {
    res.json(false);
  }
});

// POST /api/v1/download/cancel/:id
// Real backend (DownloadService.cancelDownload): keeps the row, marks it
// FAILED(4) with error "Cancelled".
router.post('/cancel/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const dl = dlStore.find(d => d.id === id);
  if (dl) {
    dl.state = 4;
    dl.error = 'Cancelled';
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
// Real backend (DownloadService.createLabel): refuses duplicates.
router.post('/label', (req, res) => {
  const body = req.body || {};
  const name = body.label || 'New Label';
  if (labelStore.some(l => l.label === name)) return res.json(false);
  labelStore.push({ id: ++nextLabelId, label: name, time: Date.now() });
  res.json(true);
});

// DELETE /api/v1/download/label/:id
// Real backend (DownloadService.deleteLabel): false when the id doesn't exist.
router.delete('/label/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!labelStore.some(l => l.id === id)) return res.json(false);
  labelStore = labelStore.filter(l => l.id !== id);
  res.json(true);
});

export default router;
