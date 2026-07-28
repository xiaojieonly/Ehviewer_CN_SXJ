// SMB network backup and sync endpoints
import { Router } from 'express';

const router = Router();

// Mutable SMB config
let smbConfig = {
  id: 1,
  host: '192.168.1.100',
  port: 445,
  share: 'backups',
  path: 'ehviewer',
  loginMode: 'GUEST',
  username: null,
  enabled: false,
};

let smbSyncState = {
  state: 'IDLE',
  totalFiles: 0,
  syncedFiles: 0,
  currentFile: '',
  speed: 0,
};

// GET /api/v1/smb/config
router.get('/config', (req, res) => {
  res.json(smbConfig);
});

// PUT /api/v1/smb/config
router.put('/config', (req, res) => {
  const body = req.body || {};
  smbConfig = { ...smbConfig, ...body, id: smbConfig.id };
  res.json(true);
});

// POST /api/v1/smb/test-connection
router.post('/test-connection', (req, res) => {
  const body = req.body || {};
  if (!body.host || !body.share) {
    return res.json({ success: false, message: 'Host and share are required' });
  }
  // Simulate successful connection
  res.json({ success: true, message: `Successfully connected to //${body.host}/${body.share}` });
});

// POST /api/v1/smb/sync
router.post('/sync', (req, res) => {
  smbSyncState = {
    state: 'RUNNING',
    totalFiles: 150,
    syncedFiles: 0,
    currentFile: 'gallery_2801001/page_001.jpg',
    speed: 5242880,
  };

  // Simulate sync progression
  const interval = setInterval(() => {
    if (smbSyncState.syncedFiles >= smbSyncState.totalFiles) {
      clearInterval(interval);
      smbSyncState.state = 'DONE';
      smbSyncState.currentFile = '';
      smbSyncState.speed = 0;
      return;
    }
    smbSyncState.syncedFiles += Math.floor(Math.random() * 5) + 1;
    if (smbSyncState.syncedFiles > smbSyncState.totalFiles) {
      smbSyncState.syncedFiles = smbSyncState.totalFiles;
    }
    smbSyncState.currentFile = `gallery_${2801001 + smbSyncState.syncedFiles % 24}/page_${String(smbSyncState.syncedFiles % 50 + 1).padStart(3, '0')}.jpg`;
  }, 500);

  res.json(true);
});

// POST /api/v1/smb/cancel
router.post('/cancel', (req, res) => {
  if (smbSyncState.state === 'RUNNING') {
    smbSyncState.state = 'CANCELLED';
    smbSyncState.speed = 0;
    res.json(true);
  } else {
    res.json(false);
  }
});

// GET /api/v1/smb/progress
router.get('/progress', (req, res) => {
  res.json(smbSyncState);
});

export default router;
