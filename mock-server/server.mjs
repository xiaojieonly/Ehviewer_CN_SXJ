// EhViewer Mock Server - Main Entry Point
import express from 'express';
import cors from 'cors';
import http from 'http';

import authRoutes from './routes/auth.mjs';
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
import { setupWebSocket } from './ws/progress.mjs';

const app = express();
const PORT = process.env.PORT || 8080;
const MOCK_DELAY_MS = parseInt(process.env.MOCK_DELAY_MS, 10) || 100;

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

// Mount routes under /api/v1
app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/gallery', galleryRoutes);
app.use('/api/v1/favorite', favoriteRoutes);
app.use('/api/v1/history', historyRoutes);
app.use('/api/v1/download', downloadRoutes);
app.use('/api/v1/comment', commentRoutes);
app.use('/api/v1/settings', settingsRoutes);
app.use('/api/v1/image', imageRoutes);
app.use('/api/v1/cache', cacheRoutes);
app.use('/api/v1/process', processRoutes);
app.use('/api/v1/sync', syncRoutes);
app.use('/api/v1/archive', archiveRoutes);
app.use('/api/v1/torrent', torrentRoutes);
app.use('/api/v1/smb', smbRoutes);
app.use('/api/v1', healthRoutes); // /health and /metrics

// Root info endpoint
app.get('/', (req, res) => {
  res.json({
    name: 'EhViewer Mock Server',
    version: '1.0.0',
    description: 'Mock backend for EhViewer WebUI frontend development',
    endpoints: {
      auth: '/api/v1/auth/{register,login,status,logout}',
      gallery: '/api/v1/gallery/{search,:gid,history,favorites,quick-search}',
      favorite: '/api/v1/favorite/{list,add,remove}',
      history: '/api/v1/history/{list,clear}',
      download: '/api/v1/download/{list,info,:id,add,start,start-all,pause,cancel,delete,label}',
      comment: '/api/v1/comment/{list/:gid,post,vote}',
      settings: '/api/v1/settings',
      image: '/api/v1/image/{proxy,cache/status,cache/clear,:galleryId/:page}',
      cache: '/api/v1/cache/{stats,gallery/:id,clear}',
      process: '/api/v1/process/{gallery/:id,status/:taskId}',
      sync: '/api/v1/sync/{push,pull,status}',
      archive: '/api/v1/archive/{list/:gid,download}',
      torrent: '/api/v1/torrent/{list/:gid,download}',
      smb: '/api/v1/smb/{config,test-connection,sync,cancel,progress}',
      health: '/api/v1/health',
      metrics: '/api/v1/metrics',
      websocket: '/ws (STOMP over SockJS)',
    },
    config: {
      port: PORT,
      mockDelayMs: MOCK_DELAY_MS,
    },
  });
});

// 404 handler for unknown API routes
app.use('/api', (req, res) => {
  res.status(404).json({
    error: 'Not Found',
    message: `No mock endpoint for ${req.method} ${req.originalUrl}`,
    hint: 'Check contracts/openapi.yaml for available endpoints',
  });
});

// Create HTTP server and attach WebSocket
const server = http.createServer(app);
setupWebSocket(server);

server.listen(PORT, () => {
  console.log('');
  console.log('╔══════════════════════════════════════════════╗');
  console.log('║       EhViewer Mock Server v1.0.0           ║');
  console.log('╠══════════════════════════════════════════════╣');
  console.log(`║  HTTP:      http://localhost:${PORT}           ║`);
  console.log(`║  WebSocket: ws://localhost:${PORT}/ws          ║`);
  console.log(`║  Delay:     ${MOCK_DELAY_MS}ms                          ║`);
  console.log('╠══════════════════════════════════════════════╣');
  console.log('║  Endpoints:                                  ║');
  console.log('║    GET  /api/v1/gallery/search               ║');
  console.log('║    GET  /api/v1/gallery/:gid                 ║');
  console.log('║    GET  /api/v1/download/list                ║');
  console.log('║    GET  /api/v1/image/:galleryId/:page       ║');
  console.log('║    GET  /api/v1/health                       ║');
  console.log('║    GET  /api/v1/metrics                      ║');
  console.log('║    ... and 30+ more (see GET /)              ║');
  console.log('╚══════════════════════════════════════════════╝');
  console.log('');
});
