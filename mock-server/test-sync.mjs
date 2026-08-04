// Automated tests for the mock sync routes (routes/sync.mjs).
//
// Run with:  node --test mock-server/test-sync.mjs
// Exit code 0 = all pass, non-zero = at least one failure.
//
// M-0 note (how these tests reach the API simulation): since the M-0 split,
// server.mjs is a gallery-SITE-only mock by default and the /api/v1/*
// backend-API simulation is opt-in (MOCK_API_SIM=1, see api-sim.mjs). These
// tests therefore do NOT boot server.mjs; they mount the sync route module
// DIRECTLY on an in-process express app — i.e. they connect straight to the
// API-sim's fixture store (fixtures/sync.mjs), which is the unit under test.
// The server-level MOCK_API_SIM gating (api sim off by default, mounted when
// enabled, site surface untouched either way) is covered by test-api-sim.mjs.
//
// The server under test is started in-process on a random port. The store is
// seeded from the fixtures at module load, so assertions must key on pushed
// entity keys rather than exact collection sizes.

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import syncRoutes from './routes/sync.mjs';

let server;
let baseUrl;

before(async () => {
  const app = express();
  app.use(express.json());
  app.use('/api/v1/sync', syncRoutes);
  server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address();
  baseUrl = `http://127.0.0.1:${port}`;
});

after(() => {
  if (server) {
    server.close();
  }
});

async function push(body) {
  const res = await fetch(`${baseUrl}/api/v1/sync/push`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: res.status, json: await res.json() };
}

async function pull(since) {
  const res = await fetch(`${baseUrl}/api/v1/sync/pull?since=${since}`);
  return { status: res.status, json: await res.json() };
}

test('pull with since=0 returns zero-lastModified records (full pull)', async () => {
  // A record pushed with lastModified=0 must be returned by a since=0 pull,
  // mirroring the backend SyncService.include() special case.
  await push({
    deviceId: 'dev-zero',
    entities: {
      history: [{ gid: 5555, lastModified: 0 }],
    },
  });
  const { status, json } = await pull(0);
  assert.equal(status, 200);
  const history = json.entities.history;
  assert.ok(Array.isArray(history));
  assert.ok(history.some((h) => h.gid === 5555 && h.lastModified === 0),
    'since=0 pull must include lastModified=0 records');
});

test('incremental pull excludes records not newer than since', async () => {
  const t0 = Date.now() - 100_000;
  await push({
    deviceId: 'dev-incr',
    entities: {
      favorites: [{ gid: 1001, lastModified: t0 }],
      downloads: [{ gid: 2001, lastModified: t0 }],
    },
  });
  const { json } = await pull(t0);
  assert.equal(json.entities.favorites.length, 0, 'record not newer than since must be excluded');
  assert.equal(json.entities.downloads.length, 0);
});

test('incremental pull returns records with lastModified > since', async () => {
  const old = Date.now() - 100_000;
  const fresh = Date.now();
  await push({
    deviceId: 'dev-incr2',
    entities: {
      favorites: [
        { gid: 1002, lastModified: old },
        { gid: 1003, lastModified: fresh },
      ],
    },
  });
  const { json } = await pull(old);
  const gids = json.entities.favorites.map((f) => f.gid);
  assert.ok(!gids.includes(1002), 'stale record must be excluded');
  assert.ok(gids.includes(1003), 'fresh record must be included');
});

test('push then pull round-trips all entity collections', async () => {
  const now = Date.now();
  await push({
    deviceId: 'dev-rt',
    entities: {
      favorites: [{ gid: 3001, lastModified: now }],
      history: [{ gid: 3002, lastModified: now }],
      downloads: [{ gid: 3003, lastModified: now }],
      bookmarks: [{ gid: 3004, lastModified: now }],
      filters: [{ mode: 'tag', text: 'demo', lastModified: now }],
      quickSearches: [{ name: 'qs', lastModified: now }],
      downloadLabels: [{ label: 'Lbl', lastModified: now }],
      preferences: { preferences: '{"general":{}}', lastModified: now, deviceId: 'dev-rt' },
    },
  });
  const { json } = await pull(0);
  const e = json.entities;
  // The store is seeded from fixtures, so assert membership by key rather
  // than exact collection lengths.
  assert.ok(e.favorites.some((f) => f.gid === 3001), 'pushed favorite must round-trip');
  assert.ok(e.history.some((h) => h.gid === 3002), 'pushed history must round-trip');
  assert.ok(e.downloads.some((d) => d.gid === 3003), 'pushed download must round-trip');
  assert.ok(e.bookmarks.some((b) => b.gid === 3004), 'pushed bookmark must round-trip');
  assert.ok(e.filters.some((f) => f.mode === 'tag' && f.text === 'demo'), 'pushed filter must round-trip');
  assert.ok(e.quickSearches.some((q) => q.name === 'qs'), 'pushed quick search must round-trip');
  assert.ok(e.downloadLabels.some((l) => l.label === 'Lbl'), 'pushed download label must round-trip');
  assert.equal(typeof e.preferences.preferences, 'string');
});

test('history tombstone push is retained and returned by incremental pull', async () => {
  const t = Date.now();
  await push({
    deviceId: 'dev-tomb',
    entities: { history: [{ gid: 4001, lastModified: t, deleted: true }] },
  });
  const full = await pull(0);
  const h = full.json.entities.history.find((x) => x.gid === 4001);
  assert.ok(h, 'tombstone row must be retained in the store');
  assert.equal(h.deleted, true, 'tombstone must keep deleted=true');
  const incr = await pull(t - 1);
  const hi = incr.json.entities.history.find((x) => x.gid === 4001);
  assert.ok(hi, 'since>0 pull must return the tombstone');
  assert.equal(hi.deleted, true);
});

test('history tombstone over a live row bumps lastModified and keeps the row', async () => {
  const t0 = Date.now() - 10_000;
  await push({
    deviceId: 'dev-tomb2',
    entities: { history: [{ gid: 4002, lastModified: t0, deleted: false }] },
  });
  const t1 = Date.now();
  await push({
    deviceId: 'dev-tomb2',
    entities: { history: [{ gid: 4002, lastModified: t1, deleted: true }] },
  });
  const { json } = await pull(t0);
  const h = json.entities.history.find((x) => x.gid === 4002);
  assert.ok(h, 'tombstoned live row must still exist');
  assert.equal(h.deleted, true, 'row must be flagged deleted');
  assert.ok(h.lastModified > t0, 'tombstone must bump lastModified past the high-water mark');
});

test('bookmark tombstone push is retained and returned by pull', async () => {
  const t = Date.now();
  await push({
    deviceId: 'dev-tomb3',
    entities: { bookmarks: [{ gid: 4003, lastModified: t, deleted: true }] },
  });
  const { json } = await pull(0);
  const b = json.entities.bookmarks.find((x) => x.gid === 4003);
  assert.ok(b, 'bookmark tombstone row must be retained in the store');
  assert.equal(b.deleted, true);
});

test('history mode field round-trips push to pull', async () => {
  const t = Date.now();
  await push({
    deviceId: 'dev-mode',
    entities: { history: [{ gid: 4004, lastModified: t, mode: 2 }] },
  });
  const { json } = await pull(0);
  const h = json.entities.history.find((x) => x.gid === 4004);
  assert.equal(h.mode, 2, 'history mode must be passed through unchanged');
});

test('preferences push preserves the client lastModified', async () => {
  const t = Date.now();
  await push({
    deviceId: 'dev-pref',
    entities: {
      preferences: { preferences: '{"general":{}}', lastModified: t, deviceId: 'dev-pref' },
    },
  });
  const { json } = await pull(0);
  assert.equal(json.entities.preferences.lastModified, t, 'client lastModified must be preserved, not server-stamped');
});
