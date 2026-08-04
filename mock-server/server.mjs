// AnotherViewer Mock Server — Gallery SITE simulation entry point.
//
// Responsibility (M-0 split): this server impersonates the REMOTE GALLERY
// SITE (gallery.mjs: /, /g/:gid/:token, /s/..., /image/..., /t/...,
// /favorites.php, /api.php) so the real backend — pointed at it via
// anotherviewer.gallery.mock-base-url / ANOTHERVIEWER_GALLERY_MOCK_BASE_URL —
// and the Android app (BuildConfig.MOCK_EH_BASE_URL) can run full site
// workflows offline.
//
// The /api/v1/* WebUI-backend simulation is a SEPARATE concern and lives in
// api-sim.mjs. It is OFF by default and only mounted when MOCK_API_SIM=1 is
// set (standalone web-frontend development against a fake backend):
//
//   PORT=4100 node mock-server/server.mjs                 # site mock only
//   MOCK_API_SIM=1 PORT=4100 node mock-server/server.mjs  # site + API sim
//
// This module is import-safe (no listen at import time) so tests can build
// servers on random ports via createMockServer(); the listen only happens in
// the CLI entry at the bottom.

import express from 'express';
import cors from 'cors';
import http from 'http';
import { pathToFileURL } from 'node:url';

import gallerySiteRoutes from './gallery.mjs';
import { apiSimRequested, mountApiSim, attachApiSimWebSocket } from './api-sim.mjs';

const MOCK_DELAY_MS = parseInt(process.env.MOCK_DELAY_MS, 10) || 100;

/**
 * Build the mock server (no listen). Returns the http.Server so callers can
 * `listen(0)` on a random port.
 *
 * @param {object} [options]
 * @param {boolean} [options.apiSim] mount the /api/v1/* backend-API
 *   simulation (api-sim.mjs). Defaults to the MOCK_API_SIM=1 env check.
 */
export function createMockServer({ apiSim = apiSimRequested() } = {}) {
  const app = express();

  // Middleware
  app.use(cors());
  app.use(express.json());

  // Configurable response delay to simulate network latency
  if (MOCK_DELAY_MS > 0) {
    app.use((req, res, next) => {
      setTimeout(next, MOCK_DELAY_MS);
    });
  }

  // Request logging
  app.use((req, res, next) => {
    const start = Date.now();
    res.on('finish', () => {
      const ms = Date.now() - start;
      console.log(`${req.method} ${req.originalUrl} ${res.statusCode} ${ms}ms`);
    });
    next();
  });

  // Backend-API simulation (/api/v1/*) — opt-in only (M-0). Mounted before
  // the site routes; the two path spaces are disjoint (/api/v1/* vs the site
  // surface /, /g, /s, /image, /t, /favorites.php, /api.php).
  if (apiSim) {
    mountApiSim(app);
  }

  // Gallery Site simulation — the default (and only, unless apiSim) surface.
  gallerySiteRoutes(app);

  const server = http.createServer(app);
  if (apiSim) {
    // Expose the ws handle (stop() clears the broadcast timers) so tests can
    // drain the event loop after server.close(); the CLI entry ignores it.
    server.apiSimWs = attachApiSimWebSocket(server);
  }
  return server;
}

// --------------------------------------------------------------- CLI entry
//
// Run the listen loop only when this file is the CLI entry point. The
// NODE_TEST_CONTEXT check keeps `node --test mock-server/*.mjs` import-safe:
// the test runner executes files as plain `node <file>` children, so the
// argv comparison alone would still treat a test run as the CLI.

const isMainModule =
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href &&
  !process.env.NODE_TEST_CONTEXT;

if (isMainModule) {
  const PORT = process.env.PORT || 8080;
  const apiSim = apiSimRequested();
  const server = createMockServer({ apiSim });

  server.listen(PORT, () => {
    console.log('');
    console.log('╔══════════════════════════════════════════════╗');
    console.log('║       AnotherViewer Mock Server v1.0.0       ║');
    console.log('╠══════════════════════════════════════════════╣');
    console.log(`║  HTTP:      http://localhost:${PORT}           ║`);
    console.log(`║  Delay:     ${MOCK_DELAY_MS}ms                          ║`);
    if (apiSim) {
      console.log('║  Mode:      gallery site + /api/v1 API sim   ║');
      console.log('║             (MOCK_API_SIM=1)                 ║');
      console.log(`║  WebSocket: ws://localhost:${PORT}/ws          ║`);
      console.log('╠══════════════════════════════════════════════╣');
      console.log('║  API sim endpoints (fake WebUI backend):     ║');
      console.log('║    GET  /api/v1/gallery/search               ║');
      console.log('║    GET  /api/v1/gallery/:gid                 ║');
      console.log('║    GET  /api/v1/download/list                ║');
      console.log('║    GET  /api/v1/image/:galleryId/:page       ║');
      console.log('║    GET  /api/v1/health                       ║');
      console.log('║    GET  /api/v1/metrics                      ║');
      console.log('║    POST /api/v1/sync/{push,pull}             ║');
      console.log('║    ... and 30+ more (see api-sim.mjs)        ║');
    } else {
      console.log('║  Mode:      gallery SITE simulation only     ║');
      console.log('║             (/api/v1 sim off; MOCK_API_SIM=1 ║');
      console.log('║             to enable, see api-sim.mjs)      ║');
      console.log('╠══════════════════════════════════════════════╣');
      console.log('║  Site endpoints (backend mock-base-url ->):  ║');
      console.log('║    GET  /?f_search=...        gallery list   ║');
      console.log('║    GET  /favorites.php        cloud favs     ║');
      console.log('║    GET  /g/:gid/:token        detail page    ║');
      console.log('║    GET  /s/:token/:gid-page   image page     ║');
      console.log('║    GET  /image/:gid/:page     raw image      ║');
      console.log('║    GET  /t/:gid/:name         thumbnails     ║');
      console.log('║    POST /api.php              gdata/showpage ║');
    }
    console.log('╚══════════════════════════════════════════════╝');
    console.log('');
  });
}
