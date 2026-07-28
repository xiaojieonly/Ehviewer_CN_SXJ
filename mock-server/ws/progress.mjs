// Simulated download/processing progress via STOMP over SockJS
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

// Active simulated downloads
const activeDownloads = [
  {
    taskId: 'dl-2801001',
    galleryId: galleries[0].gid,
    galleryName: galleries[0].title,
    downloadedPages: Math.floor(galleries[0].pages * 0.6),
    totalPages: galleries[0].pages,
    speed: 2097152,
    state: 'downloading',
    label: 1,
  },
  {
    taskId: 'dl-2801003',
    galleryId: galleries[2].gid,
    galleryName: galleries[2].title,
    downloadedPages: 0,
    totalPages: galleries[2].pages,
    speed: 1048576,
    state: 'downloading',
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
            server: 'ehviewer-mock/1.0',
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
              const pong = envelope('system.pong', {
                clientTime: body.clientTime,
                serverTime: Date.now(),
              });
              // Send pong to /topic/pong for all subscriptions matching it
              for (const [, dest] of subscriptions) {
                if (dest === '/topic/pong') {
                  conn.write(stompFrame('MESSAGE', {
                    destination: dest,
                    'content-type': 'application/json',
                    subscription: subscriptions.keys().next().value,
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
      if (dl.state === 'downloading') {
        dl.downloadedPages = Math.min(dl.downloadedPages + Math.floor(Math.random() * 3) + 1, dl.totalPages);
        dl.speed = Math.floor(Math.random() * 3145728) + 524288; // 0.5-3.5 MB/s

        if (dl.downloadedPages >= dl.totalPages) {
          dl.state = 'completed';
          dl.speed = 0;
        }

        const msg = envelope('download.progress', {
          taskId: dl.taskId,
          galleryId: dl.galleryId,
          galleryName: dl.galleryName,
          downloadedPages: dl.downloadedPages,
          totalPages: dl.totalPages,
          speed: dl.speed,
          state: dl.state,
          label: dl.label,
        });

        broadcast(connections, ['/topic/download/progress', `/topic/download/${dl.galleryId}`], msg);

        // Also send state change when completed
        if (dl.state === 'completed') {
          const stateMsg = envelope('download.state', {
            taskId: dl.taskId,
            galleryId: dl.galleryId,
            state: 'completed',
            previousState: 'downloading',
          });
          broadcast(connections, ['/topic/download/state', `/topic/download/${dl.galleryId}`], stateMsg);
        }
      }
    }

    // Reset completed downloads to simulate continuous activity
    for (const dl of activeDownloads) {
      if (dl.state === 'completed') {
        dl.downloadedPages = 0;
        dl.state = 'downloading';
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

      // Reset
      activeProcess.processedPages = 0;
      activeProcess.currentPage = 1;
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
      activeDownloads: activeDownloads.filter(d => d.state === 'downloading').length,
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
