// Sync push/pull/status endpoints (matches contracts/sync-schemas.json + SyncService.kt merge semantics)
import { Router } from 'express';
import {
  syncFavorites, syncHistory, syncDownloads, syncBookmarks,
  syncFilters, syncQuickSearches, syncDownloadLabels, connectedDevices,
} from '../fixtures/sync.mjs';

const SKEW_TOLERANCE = 5000;

const store = {
  favorites: new Map(),
  history: new Map(),
  downloads: new Map(),
  bookmarks: new Map(),
  filters: new Map(),
  quickSearches: new Map(),
  downloadLabels: new Map(),
};

const preferences = { preferences: '{}', lastModified: 0, deviceId: '' };
const devices = new Map();

function seed() {
  syncFavorites.forEach((e) => store.favorites.set(e.gid, { ...e }));
  syncHistory.forEach((e) => store.history.set(e.gid, { ...e }));
  syncDownloads.forEach((e) => store.downloads.set(e.gid, { ...e }));
  syncBookmarks.forEach((e) => store.bookmarks.set(e.gid, { ...e }));
  syncFilters.forEach((e) => store.filters.set(`${e.mode}:${e.text}`, { ...e }));
  syncQuickSearches.forEach((e) => store.quickSearches.set(e.name, { ...e }));
  syncDownloadLabels.forEach((e) => store.downloadLabels.set(e.label, { ...e }));
  const now = Date.now();
  connectedDevices.forEach((d) => devices.set(d.deviceId, {
    ...d,
    deviceName: d.deviceName ?? null,
    pairedAt: now - 86400000,
    lastSyncTimestamp: d.lastSeen,
  }));
}
seed();

function updateDevice(deviceId, timestamp) {
  const device = devices.get(deviceId);
  if (device) {
    device.lastSeen = timestamp;
    device.lastSyncTimestamp = timestamp;
  } else {
    devices.set(deviceId, {
      deviceId,
      deviceName: null,
      platform: deviceId.split('-')[0] || 'other',
      pairedAt: timestamp,
      lastSeen: timestamp,
      lastSyncTimestamp: timestamp,
    });
  }
}

function mergeFavorite(existing, incoming) {
  if (existing === undefined) { store.favorites.set(incoming.gid, { ...incoming }); return false; }
  if (incoming.deleted && !existing.deleted) return false;
  if (existing.deleted && !incoming.deleted) { Object.assign(existing, incoming); return true; }
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeHistory(existing, incoming) {
  if (incoming.deleted) { if (existing !== undefined) store.history.delete(incoming.gid); return false; }
  if (existing === undefined) { store.history.set(incoming.gid, { ...incoming }); return false; }
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false;
  if (incoming.time > existing.time) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeDownload(existing, incoming) {
  if (existing === undefined) { store.downloads.set(incoming.gid, { ...incoming }); return false; }
  if (incoming.deleted && !existing.deleted) return false;
  if (existing.deleted && !incoming.deleted) { Object.assign(existing, incoming); return true; }
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeBookmark(existing, incoming) {
  if (incoming.deleted) { if (existing !== undefined) store.bookmarks.delete(incoming.gid); return false; }
  if (existing === undefined) { store.bookmarks.set(incoming.gid, { ...incoming }); return false; }
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false;
  if (incoming.page > existing.page) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeFilter(existing, incoming) {
  if (existing === undefined) { store.filters.set(`${incoming.mode}:${incoming.text}`, { ...incoming }); return false; }
  if (incoming.deleted && !existing.deleted) return false;
  if (existing.deleted && !incoming.deleted) { Object.assign(existing, incoming); return true; }
  if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false;
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  if (incoming.enabled !== existing.enabled && incoming.enabled) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeQuickSearch(existing, incoming) {
  if (existing === undefined) { store.quickSearches.set(incoming.name, { ...incoming }); return false; }
  if (incoming.deleted && !existing.deleted) return false;
  if (existing.deleted && !incoming.deleted) { Object.assign(existing, incoming); return true; }
  if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false;
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  return false;
}

function mergeDownloadLabel(existing, incoming) {
  if (existing === undefined) { store.downloadLabels.set(incoming.label, { ...incoming }); return false; }
  if (incoming.deleted && !existing.deleted) return false;
  if (existing.deleted && !incoming.deleted) { Object.assign(existing, incoming); return true; }
  if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false;
  if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) { Object.assign(existing, incoming); return true; }
  return false;
}

const router = Router();

// POST /api/v1/sync/push — body {entities, deviceId, timestamp}
router.post('/push', (req, res) => {
  const body = req.body || {};
  const e = body.entities || {};
  let conflicts = 0;

  (e.favorites || []).forEach((v) => { if (mergeFavorite(store.favorites.get(v.gid), v)) conflicts += 1; });
  (e.history || []).forEach((v) => { if (mergeHistory(store.history.get(v.gid), v)) conflicts += 1; });
  (e.downloads || []).forEach((v) => { if (mergeDownload(store.downloads.get(v.gid), v)) conflicts += 1; });
  (e.bookmarks || []).forEach((v) => { if (mergeBookmark(store.bookmarks.get(v.gid), v)) conflicts += 1; });
  (e.filters || []).forEach((v) => { if (mergeFilter(store.filters.get(`${v.mode}:${v.text}`), v)) conflicts += 1; });
  (e.quickSearches || []).forEach((v) => { if (mergeQuickSearch(store.quickSearches.get(v.name), v)) conflicts += 1; });
  (e.downloadLabels || []).forEach((v) => { if (mergeDownloadLabel(store.downloadLabels.get(v.label), v)) conflicts += 1; });

  if (e.preferences && typeof e.preferences.preferences === 'string') {
    preferences.preferences = e.preferences.preferences;
    preferences.lastModified = e.preferences.lastModified || Date.now();
    preferences.deviceId = e.preferences.deviceId || '';
  }

  const now = Date.now();
  if (body.deviceId) updateDevice(body.deviceId, now);

  res.json({ success: true, serverTimestamp: now, conflicts });
});

// GET /api/v1/sync/pull?since={ts}&deviceId={id}
router.get('/pull', (req, res) => {
  const since = parseInt(req.query.since, 10) || 0;
  const deviceId = req.query.deviceId || '';
  const now = Date.now();
  if (deviceId) updateDevice(deviceId, now);

  const filterBy = (m) => [...m.values()].filter((v) => v.lastModified > since);
  res.json({
    entities: {
      favorites: filterBy(store.favorites),
      history: filterBy(store.history),
      downloads: filterBy(store.downloads),
      bookmarks: filterBy(store.bookmarks),
      filters: filterBy(store.filters),
      quickSearches: filterBy(store.quickSearches),
      downloadLabels: filterBy(store.downloadLabels),
      preferences: { ...preferences },
    },
    serverTimestamp: now,
  });
});

// GET /api/v1/sync/status
router.get('/status', (req, res) => {
  res.json({
    lastSyncTimestamp: [...devices.values()].reduce((m, d) => Math.max(m, d.lastSyncTimestamp || 0), 0),
    connectedDevices: [...devices.values()].map((d) => ({
      deviceId: d.deviceId,
      deviceName: d.deviceName ?? null,
      platform: d.platform,
      lastSeen: d.lastSeen,
    })),
    entityCounts: {
      favorites: store.favorites.size,
      history: store.history.size,
      downloads: store.downloads.size,
      bookmarks: store.bookmarks.size,
      filters: store.filters.size,
      quickSearches: store.quickSearches.size,
      downloadLabels: store.downloadLabels.size,
    },
  });
});

// GET /api/v1/sync/devices — paired devices
router.get('/devices', (req, res) => {
  res.json([...devices.values()]
    .map((d) => ({
      deviceId: d.deviceId,
      deviceName: d.deviceName || d.deviceId,
      platform: d.platform || 'other',
      pairedAt: d.pairedAt,
      lastSeen: d.lastSeen,
    }))
    .sort((a, b) => b.pairedAt - a.pairedAt));
});

// DELETE /api/v1/sync/devices/:deviceId — revoke a device
router.delete('/devices/:deviceId', (req, res) => {
  const { deviceId } = req.params;
  if (devices.delete(deviceId)) return res.json({ success: true });
  res.status(403).json({ success: false, message: 'Device not found or not owned by this user' });
});

export default router;
