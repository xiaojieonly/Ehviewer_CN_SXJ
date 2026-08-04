// Backend API simulation (/api/v1/*) — SEPARATE concern from the Gallery Site
// mock (gallery.mjs).
//
// mock-server's primary job is to stand in for the REMOTE GALLERY SITE so the
// real backend (via anotherviewer.gallery.mock-base-url /
// ANOTHERVIEWER_GALLERY_MOCK_BASE_URL) and the Android app (via
// BuildConfig.MOCK_EH_BASE_URL) can be exercised without the real site.
//
// The routes mounted here instead impersonate OUR OWN WebUI backend
// (/api/v1/gallery, /api/v1/sync, ...). That is only useful for standalone
// web-frontend development against a fake backend, and mixing it into the
// site mock by default blurred responsibilities (M-0). It is therefore
// opt-in:
//
//   - server.mjs mounts this module ONLY when MOCK_API_SIM=1 is set
//     (see apiSimRequested()); default = off = pure site mock.
//   - tests that need the API simulation either mount the route modules
//     directly (see test-sync.mjs) or build a server with the apiSim flag.
//
// Nothing in this file talks to a real gallery site; fixtures are synthetic
// (fixtures/*.mjs).

import authRoutes, { validateToken, isAuthRequired } from './routes/auth.mjs';
import galleryRoutes from './routes/gallery.mjs';
import favoriteRoutes from './routes/favorite.mjs';
import historyRoutes from './routes/history.mjs';
import downloadRoutes from './routes/download.mjs';
import commentRoutes from './routes/comment.mjs';
import settingsRoutes from './routes/settings.mjs';
import imageRoutes from './routes/image.mjs';
import cacheRoutes from './routes/cache.mjs';
import processRoutes from './routes/process.mjs';
import syncRoutes from './routes/sync.mjs';
import archiveRoutes from './routes/archive.mjs';
import torrentRoutes from './routes/torrent.mjs';
import smbRoutes from './routes/smb.mjs';
import healthRoutes from './routes/health.mjs';
import proxyRoutes from './routes/proxy.mjs';
import preferenceRoutes from './routes/preferences.mjs';
import { setupWebSocket } from './ws/progress.mjs';

/** True when the process asked for the backend-API simulation (MOCK_API_SIM=1). */
export function apiSimRequested() {
  return process.env.MOCK_API_SIM === '1';
}

// Auth guard for protected /api/** routes. Helpers come from routes/auth.mjs
// (named exports): validateToken(token) -> boolean, isAuthRequired() -> boolean.
// When require_auth is off (default) all /api routes stay public, matching the
// backend's behavior when security.require_auth = false.
const PERMIT_ALL_API_PATHS = new Set([
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/status',
  '/api/v1/auth/pair/complete',
  '/api/v1/health',
]);

/**
 * Mount the /api/v1/* backend-API simulation onto `app`.
 *
 * NOTE: the site route POST /api.php (gallery.mjs) does NOT match the /api
 * prefix (Express path prefixes require a `/` boundary), so this guard never
 * touches site traffic.
 */
export function mountApiSim(app) {
  app.use('/api', (req, res, next) => {
    const fullPath = req.baseUrl + req.path;
    if (PERMIT_ALL_API_PATHS.has(fullPath) || fullPath.startsWith('/api/v1/metrics')) {
      return next();
    }
    if (!isAuthRequired()) {
      return next();
    }
    const auth = req.headers.authorization || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
    if (token && validateToken(token)) {
      return next();
    }
    // The real backend's entry point (SecurityConfig) returns 401, not 403.
    res.status(401).json({ success: false, message: 'Authentication required' });
  });

  // Mount routes under /api/v1
  app.use('/api/v1/auth', authRoutes);
  app.use('/api/v1/gallery', galleryRoutes);
  app.use('/api/v1/favorite', favoriteRoutes);
  app.use('/api/v1/history', historyRoutes);
  app.use('/api/v1/download', downloadRoutes);
  app.use('/api/v1/comment', commentRoutes);
  app.use('/api/v1/settings', settingsRoutes);
  app.use('/api/v1/proxy', proxyRoutes);
  app.use('/api/v1/preferences', preferenceRoutes);
  app.use('/api/v1/image', imageRoutes);
  app.use('/api/v1/cache', cacheRoutes);
  app.use('/api/v1/process', processRoutes);
  app.use('/api/v1/sync', syncRoutes);
  app.use('/api/v1/archive', archiveRoutes);
  app.use('/api/v1/torrent', torrentRoutes);
  app.use('/api/v1/smb', smbRoutes);
  app.use('/api/v1', healthRoutes); // /health and /metrics

  // 404 handler for unknown API routes
  app.use('/api', (req, res) => {
    res.status(404).json({
      error: 'Not Found',
      message: `No mock endpoint for ${req.method} ${req.originalUrl}`,
      hint: 'Check contracts/openapi.yaml for available endpoints',
    });
  });
}

/**
 * Attach the API-simulation WebSocket (STOMP over SockJS on /ws) to an
 * already-created http server. Part of the backend-API simulation: it fakes
 * the real backend's progress topics (see ws/progress.mjs header).
 * Returns the ws handle ({ stop() }) so test harnesses can clear the
 * broadcast timers after closing the server.
 */
export function attachApiSimWebSocket(server) {
  return setupWebSocket(server);
}
