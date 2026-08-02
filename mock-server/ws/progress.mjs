// Simulated download/processing progress via STOMP over SockJS
//
// Download messages mirror the REAL backend exactly (see
// anotherviewer-web/.../websocket/DownloadProgressHandler.kt): the bare
// DownloadProgress DTO {gid, state, downloaded, total, speed, label} is
// published on BOTH /topic/download/{gid} AND /topic/download/all.
//
// NOTE: the real handler publishes NO envelope topics, so
// /topic/download/progress and /topic/download/state are intentionally NOT
// emitted here (contract §7.4 migration: the frontend's subscribeAll /
// subscribeDownload parse the bare DTO from the legacy topics). Enveloped
// messages remain only for process.* / system.health.
import sockjs from 'sockjs';
import { galleries } from '../fixtures/galleries.mjs';

const VERSION = '1.0';

function envelope(type, payload) {
  return JSON.stringify({
    type,
    timestamp: Date.now(),
    version: VERSION,
    payload,
  });
}

// STOMP frame helpers
const NULL_BYTE = '\x00';

function stompFrame(command, headers = {}, body = '') {
  let frame = command + '\n';
  for (const [key, value] of Object.entries(headers)) {
    frame += `${key}:${value}\n`;
  }
  frame += '\n' + body + NULL_BYTE;
  return frame;
}

function parseStompFrame(data) {
  const str = data.toString();
  const nullIdx = str.indexOf(NULL_BYTE);
  const content = nullIdx >= 0 ? str.slice(0, nullIdx) : str;
  const lines = content.split('\n');
  const command = lines[0];
  const headers = {};
  let i = 1;
  for (; i < lines.length; i++) {
    if (lines[i] === '') break;
    const colonIdx = lines[i].indexOf(':');
    if (colonIdx > 0) {
      headers[lines[i].slice(0, colonIdx)] = lines[i].slice(colonIdx + 1);
    }
  }
  const body = lines.slice(i + 1).join('\n');
  return { command, headers, body };
}

// Download state ints (match backend DTO / frontend DownloadItem.vue)
const STATE_DOWNLOADING = 2;
const STATE_FINISH = 3;

// Active simulated downloads — bare DTO shape, as DownloadService publishes it
const activeDownloads = [
  {
    gid: galleries[0].gid,
    state: STATE_DOWNLOADING,
    downloaded: Math.floor(galleries[0].pages * 0.6),
    total: galleries[0].pages,
    speed: 2097152,
    label: 1,
  },
  {
    gid: galleries[2].gid,
    state: STATE_DOWNLOADING,
    downloaded: 0,
    total: galleries[2].pages,
    speed: 1048576,
    label: 1,
  },
];

// Active simulated processing task
const activeProcess = {
  taskId: 'proc-mock-001',
  galleryId: galleries[6].gid,
  processedPages: 12,
  totalPages: 85,
  currentPage: 13,
};

export function setupWebSocket(server) {
  const sockjsServer = sockjs.createServer({
    log: () => {},  // suppress default logging
    sockjs_url: 'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js',
  });

  const connections = new Set();

  sockjsServer.on('connection', (conn) => {
    if (!conn) return;

    const subscriptions = new Map(); // sub-id -> destination
    let connected = false;

    connections.add(conn);

    conn.on('data', (message) => {
      // Handle heartbeat (newline only)
      if (message === '\n' || message === '\r\n') {
        conn.write('\n');
        return;
      }

      const frame = parseStompFrame(message);

      switch (frame.command) {
        case 'CONNECT':
        case 'STOMP': {
          connected = true;
          conn.write(stompFrame('CONNECTED', {
            version: '1.2',
            'heart-beat': '10000,10000',
            server: 'anotherviewer-mock/1.0',
          }));
          break;
        }

        case 'SUBSCRIBE': {
          const id = frame.headers.id;
          const destination = frame.headers.destination;
          if (id && destination) {
            subscriptions.set(id, destination);
          }
          // Send receipt if requested
          if (frame.headers.receipt) {
            conn.write(stompFrame('RECEIPT', { 'receipt-id': frame.headers.receipt }));
          }
          break;
        }

        case 'UNSUBSCRIBE': {
          const id = frame.headers.id;
          if (id) {
            subscriptions.delete(id);
          }
          break;
        }

        case 'SEND': {
          const destination = frame.headers.destination;
          // Handle /app/ping
          if (destination === '/app/ping') {
            try {
              const body = JSON.parse(frame.body);
              // Contract Appendix B: PongResponse is a DIRECT reply (not
              // enveloped). Address it with the subscription id of the entry
              // matching /topic/pong (NOT the first subscription in the map).
              const pong = JSON.stringify({
                clientTime: body.clientTime,
                serverTime: Date.now(),
              });
              for (const [subId, dest] of subscriptions) {
                if (dest === '/topic/pong') {
                  conn.write(stompFrame('MESSAGE', {
                    destination: dest,
                    'content-type': 'application/json',
                    subscription: subId,
                  }, pong));
                }
              }
            } catch (e) {
              // ignore parse errors
            }
          }
          // Handle /app/subscribe
          if (destination === '/app/subscribe') {
            try {
              const body = JSON.parse(frame.body);
              const confirm = envelope('subscription.confirm', {
                types: body.types || [],
                galleryIds: body.galleryIds || [],
                subscriptionId: `sub-${Date.now()}`,
              });
              for (const [subId, dest] of subscriptions) {
                if (dest === '/topic/subscription/confirm') {
                  conn.write(stompFrame('MESSAGE', {
                    destination: dest,
                    'content-type': 'application/json',
                    subscription: subId,
                  }, confirm));
                }
              }
            } catch (e) {
              // ignore
            }
          }
          break;
        }

        case 'DISCONNECT': {
          if (frame.headers.receipt) {
            conn.write(stompFrame('RECEIPT', { 'receipt-id': frame.headers.receipt }));
          }
          connected = false;
          break;
        }

        default:
          break;
      }
    });

    conn.on('close', () => {
      connections.delete(conn);
    });

    // Store helper for sending messages to this connection
    conn._subscriptions = subscriptions;
    conn._isConnected = () => connected;
  });

  sockjsServer.installHandlers(server, { prefix: '/ws' });

  // Periodic broadcast: download progress (every 2s)
  setInterval(() => {
    for (const dl of activeDownloads) {
      if (dl.state !== STATE_DOWNLOADING) continue;
      dl.downloaded = Math.min(dl.downloaded + Math.floor(Math.random() * 3) + 1, dl.total);
      dl.speed = Math.floor(Math.random() * 3145728) + 524288; // 0.5-3.5 MB/s

      if (dl.downloaded >= dl.total) {
        dl.state = STATE_FINISH;
        dl.speed = 0;
      }

      // Bare DTO exactly as DownloadProgressHandler.kt sends it, on BOTH
      // legacy topics. Envelope topics (/topic/download/progress,
      // /topic/download/state) are intentionally NOT published — the real
      // backend does not publish them.
      const dto = JSON.stringify({
        gid: dl.gid,
        state: dl.state,
        downloaded: dl.downloaded,
        total: dl.total,
        speed: dl.speed,
        label: dl.label,
      });

      broadcast(connections, ['/topic/download/all', `/topic/download/${dl.gid}`], dto);
    }

    // Reset completed downloads to simulate continuous activity
    for (const dl of activeDownloads) {
      if (dl.state === STATE_FINISH) {
        dl.downloaded = 0;
        dl.state = STATE_DOWNLOADING;
        dl.speed = 1048576;
      }
    }
  }, 2000);

  // Periodic broadcast: process progress (every 2s)
  setInterval(() => {
    activeProcess.processedPages = Math.min(activeProcess.processedPages + 1, activeProcess.totalPages);
    activeProcess.currentPage = activeProcess.processedPages + 1;

    if (activeProcess.processedPages >= activeProcess.totalPages) {
      // Send completed
      const completedMsg = envelope('process.completed', {
        taskId: activeProcess.taskId,
        galleryId: activeProcess.galleryId,
        enhancedPages: activeProcess.totalPages,
        elapsedMs: activeProcess.totalPages * 2000,
      });
      broadcast(connections, ['/topic/process/all', `/topic/process/${activeProcess.taskId}`], completedMsg);

      // Restart a fresh job (emit process.started on the new lifecycle)
      activeProcess.processedPages = 0;
      activeProcess.currentPage = 1;
      const startedMsg = envelope('process.started', {
        taskId: activeProcess.taskId,
        galleryId: activeProcess.galleryId,
        totalPages: activeProcess.totalPages,
        processingType: 'UPSCALE_2X',
        processorId: 'noop',
      });
      broadcast(connections, ['/topic/process/all', `/topic/process/${activeProcess.taskId}`], startedMsg);
    } else {
      const msg = envelope('process.progress', {
        taskId: activeProcess.taskId,
        galleryId: activeProcess.galleryId,
        processedPages: activeProcess.processedPages,
        totalPages: activeProcess.totalPages,
        currentPage: activeProcess.currentPage,
      });
      broadcast(connections, ['/topic/process/all', `/topic/process/${activeProcess.taskId}`], msg);
    }
  }, 2000);

  // Periodic broadcast: system health (every 30s)
  setInterval(() => {
    const msg = envelope('system.health', {
      cacheUsage: 1073741824,
      cacheCapacity: 5368709120,
      activeDownloads: activeDownloads.filter(d => d.state === STATE_DOWNLOADING).length,
      processingQueue: 1,
      memoryUsage: 268435456,
      memoryMax: 1073741824,
      uptimeSeconds: Math.floor(process.uptime()),
      processorAvailable: true,
    });
    broadcast(connections, ['/topic/system/health'], msg);
  }, 30000);

  console.log('[WS] STOMP over SockJS endpoint installed at /ws');
}

function broadcast(connections, destinations, messageBody) {
  for (const conn of connections) {
    if (!conn._isConnected || !conn._isConnected()) continue;
    const subs = conn._subscriptions;
    if (!subs) continue;

    for (const [subId, dest] of subs) {
      if (destinations.includes(dest)) {
        try {
          conn.write(stompFrame('MESSAGE', {
            destination: dest,
            'content-type': 'application/json',
            subscription: subId,
            'message-id': `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          }, messageBody));
        } catch (e) {
          // connection may have closed
        }
      }
    }
  }
}
