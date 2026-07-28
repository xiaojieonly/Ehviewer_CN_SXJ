// Torrent download endpoints
import { Router } from 'express';

const router = Router();

// GET /api/v1/torrent/list/:gid
router.get('/list/:gid', (req, res) => {
  const gid = parseInt(req.params.gid, 10);
  res.json({
    torrents: [
      {
        gid,
        token: `torrent-${gid}-1`,
        name: `[Gallery ${gid}] Original Quality.torrent`,
        size: '132.8 MB',
        addedTime: '2025-03-15 08:30',
      },
      {
        gid,
        token: `torrent-${gid}-2`,
        name: `[Gallery ${gid}] Compressed.torrent`,
        size: '52.1 MB',
        addedTime: '2025-03-16 14:22',
      },
    ],
  });
});

// GET /api/v1/torrent/download
router.get('/download', (req, res) => {
  res.json(true);
});

export default router;
