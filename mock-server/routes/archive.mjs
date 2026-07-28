// Archive download endpoints
import { Router } from 'express';

const router = Router();

// GET /api/v1/archive/list/:gid
router.get('/list/:gid', (req, res) => {
  const gid = parseInt(req.params.gid, 10);
  res.json({
    archives: [
      {
        gid,
        url: `https://ehgt.org/archive/${gid}/original.zip`,
        name: 'Original Archive',
        size: '125.4 MB',
        price: 'Free',
        credit: '0',
      },
      {
        gid,
        url: `https://ehgt.org/archive/${gid}/resample.zip`,
        name: 'Resample Archive',
        size: '48.2 MB',
        price: 'Free',
        credit: '0',
      },
    ],
  });
});

// POST /api/v1/archive/download
router.post('/download', (req, res) => {
  res.json(true);
});

export default router;
