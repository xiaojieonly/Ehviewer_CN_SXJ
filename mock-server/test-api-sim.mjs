// Automated tests for the M-0 split: server.mjs is a gallery-SITE-only mock
// by default; the /api/v1/* backend-API simulation (api-sim.mjs) mounts only
// when MOCK_API_SIM=1.
//
// Run with:  node --test mock-server/test-api-sim.mjs
// Exit code 0 = all pass, non-zero = at least one failure.
//
// Servers are built in-process via createMockServer() on random ports. The
// real backend consumes the SITE surface of this mock through
// anotherviewer.gallery.mock-base-url (ANOTHERVIEWER_GALLERY_MOCK_BASE_URL),
// so the default-mode assertions below are exactly what that wiring relies
// on: full site functionality, no /api/v1 impersonation.

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';

import { createMockServer } from './server.mjs';
import { apiSimRequested } from './api-sim.mjs';

// Keep the MOCK_API_SIM env deterministic regardless of the caller's shell.
const ORIGINAL_API_SIM_ENV = process.env.MOCK_API_SIM;

before(() => {
  delete process.env.MOCK_API_SIM;
});

after(() => {
  if (ORIGINAL_API_SIM_ENV === undefined) {
    delete process.env.MOCK_API_SIM;
  } else {
    process.env.MOCK_API_SIM = ORIGINAL_API_SIM_ENV;
  }
});

/** Start a server on a random port and return { server, baseUrl }. */
async function start(options) {
  const server = createMockServer(options);
  server.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address();
  return { server, baseUrl: `http://127.0.0.1:${port}` };
}

async function stop({ server }) {
  server.apiSimWs?.stop(); // clear API-sim broadcast timers (drain event loop)
  server.close();
}

// --------------------------------------------------------------- env gate

test('apiSimRequested() is false by default and true iff MOCK_API_SIM=1', () => {
  delete process.env.MOCK_API_SIM;
  assert.equal(apiSimRequested(), false, 'unset env -> api sim off');
  process.env.MOCK_API_SIM = '0';
  assert.equal(apiSimRequested(), false, 'MOCK_API_SIM=0 is not the opt-in value');
  process.env.MOCK_API_SIM = 'true';
  assert.equal(apiSimRequested(), false, 'only the literal "1" enables the api sim');
  process.env.MOCK_API_SIM = '1';
  assert.equal(apiSimRequested(), true, 'MOCK_API_SIM=1 -> api sim on');
  delete process.env.MOCK_API_SIM;
});

// ------------------------------------------------- default mode: site only

test('default server: /api/v1/* is NOT served (backend-API sim off)', async () => {
  const ctx = await start({ apiSim: false });
  try {
    const health = await fetch(`${ctx.baseUrl}/api/v1/health`);
    assert.equal(health.status, 404, 'no /api/v1 routes mounted by default');
    const sync = await fetch(`${ctx.baseUrl}/api/v1/sync/pull?since=0`);
    assert.equal(sync.status, 404);
  } finally {
    await stop(ctx);
  }
});

test('default server: site surface is fully up (backend mock-base-url target)', async () => {
  const ctx = await start({ apiSim: false });
  try {
    // List page (search) — what GalleryService hits first.
    const list = await fetch(`${ctx.baseUrl}/?f_search=alpha`);
    assert.equal(list.status, 200);
    assert.match(list.headers.get('content-type'), /^text\/html/);
    assert.ok((await list.text()).includes('Test Gallery Alpha'));

    // Detail page — GalleryDetailParser contract (gid script + page count).
    const detail = await fetch(`${ctx.baseUrl}/g/1001/aaa1111111/`);
    assert.equal(detail.status, 200);
    const detailBody = await detail.text();
    assert.ok(detailBody.includes('var gid = 1001'));
    assert.ok(detailBody.includes('5 pages'));

    // Image page + raw image bytes (reader / download path).
    const page = await fetch(`${ctx.baseUrl}/s/aaa1111111/1001-1`);
    assert.equal(page.status, 200);
    const img = await fetch(`${ctx.baseUrl}/image/1001/1.jpg`);
    assert.equal(img.status, 200);
    const magic = Buffer.from(await img.arrayBuffer()).subarray(0, 3);
    assert.deepEqual([...magic], [0xff, 0xd8, 0xff], 'JPEG magic bytes');

    // Cloud favorites page (R4-8) — FavoritesParser contract.
    const favs = await fetch(`${ctx.baseUrl}/favorites.php`);
    assert.equal(favs.status, 200);
    assert.ok((await favs.text()).includes('class="ido"'));

    // Site API (api.php gdata) — note: /api.php must NOT be caught by any
    // /api guard; in default mode there is none, and even with the api sim
    // mounted the Express path prefix /api never matches /api.php.
    const gdata = await fetch(`${ctx.baseUrl}/api.php`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ method: 'gdata', gidlist: [[1001, 'aaa1111111']] }),
    });
    assert.equal(gdata.status, 200);
    const gjson = await gdata.json();
    assert.equal(gjson.gmetadata[0].gid, 1001);
  } finally {
    await stop(ctx);
  }
});

// ------------------------------------- MOCK_API_SIM=1: site + API sim

test('MOCK_API_SIM=1 mounts /api/v1/* alongside an untouched site surface', async () => {
  // Exercise the real opt-in path: env -> createMockServer() default option.
  process.env.MOCK_API_SIM = '1';
  const ctx = await start();
  try {
    // API sim is up: health endpoint answers like the fake backend.
    const health = await fetch(`${ctx.baseUrl}/api/v1/health`);
    assert.equal(health.status, 200);
    assert.equal((await health.json()).status, 'UP');

    // Sync routes round-trip through the full server.
    const now = Date.now();
    const push = await fetch(`${ctx.baseUrl}/api/v1/sync/push`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        deviceId: 'dev-m0-wiring',
        entities: { history: [{ gid: 9001, lastModified: now }] },
      }),
    });
    assert.equal(push.status, 200);
    const pull = await fetch(`${ctx.baseUrl}/api/v1/sync/pull?since=0`);
    assert.equal(pull.status, 200);
    const pulled = await pull.json();
    assert.ok(
      pulled.entities.history.some((h) => h.gid === 9001),
      'pushed record must round-trip via the full server',
    );

    // Site surface still fully functional in the same process.
    const list = await fetch(`${ctx.baseUrl}/?f_search=alpha`);
    assert.equal(list.status, 200);
    assert.ok((await list.text()).includes('Test Gallery Alpha'));
    const detail = await fetch(`${ctx.baseUrl}/g/1001/aaa1111111/`);
    assert.equal(detail.status, 200);
  } finally {
    delete process.env.MOCK_API_SIM;
    await stop(ctx);
  }
});
