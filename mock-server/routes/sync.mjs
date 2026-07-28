// Sync push/pull/status endpoints
import { Router } from 'express';
import {
  syncFavorites, syncHistory, syncDownloads, syncBookmarks,
  syncFilters, syncQuickSearches, syncDownloadLabels, connectedDevices,
} from '../fixtures/sync.mjs';

const router = Router();

// POST /api/v1/sync/push
router.post('/push', (req, res) => {
  const body = req.body || {};
  const favorites = body.favorites || [];
  const history = body.history || [];
  const downloads = body.downloads || [];
  const readingProgress = body.readingProgress || [];
  const quickSearches = body.quickSearches || [];

  // Count conflicts (simulate a few)
  const totalPushed = favorites.length + history.length + downloads.length + readingProgress.length + quickSearches.length;
  const conflicts = Math.min(2, Math.floor(totalPushed / 3));

  res.json({
    success: true,
    serverTimestamp: Date.now(),
    conflicts,
  });
});

// GET /api/v1/sync/pull
router.get('/pull', (req, res) => {
  const since = parseInt(req.query.since, 10) || 0;

  // Return entities modified after 'since'
  res.json({
    serverTimestamp: Date.now(),
    favorites: syncFavorites.filter(f => f.lastModified > since),
    history: syncHistory.filter(h => h.lastModified > since),
    downloads: syncDownloads.filter(d => d.lastModified > since),
    readingProgress: syncBookmarks.filter(b => b.lastModified > since).map(b => ({
      gid: b.gid,
      page: b.page,
      time: b.time,
    })),
    quickSearches: syncQuickSearches.filter(q => q.lastModified > since).map(q => ({
      id: 0,
      name: q.name,
      mode: q.mode,
      category: q.category,
      keyword: q.keyword,
      advanceSearch: q.advanceSearch,
      minRating: q.minRating,
      pageFrom: q.pageFrom,
      pageTo: q.pageTo,
    })),
  });
});

// GET /api/v1/sync/status
router.get('/status', (req, res) => {
  res.json({
    lastSyncTimestamp: Date.now() - 60000,
    connectedDevices: connectedDevices.length,
    syncInProgress: false,
    totalFavorites: syncFavorites.length,
    totalHistory: syncHistory.length,
    totalDownloads: syncDownloads.length,
  });
});

export default router;
