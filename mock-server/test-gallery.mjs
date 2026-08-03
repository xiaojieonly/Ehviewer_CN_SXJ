// Automated tests for the Gallery Site mock server (gallery.mjs).
//
// Run with:  node --test test-gallery.mjs   (or: node test-gallery.mjs)
// Exit code 0 = all pass, non-zero = at least one failure.
//
// The server under test is started in-process on a random port; fixtures and
// image generation come from the shared modules:
//   - gallery-fixtures.mjs  -> GALLERIES, findGallery, EXH_BASE
//   - gallery-images.mjs    -> makeImage(gid, page, format)

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import galleryRoutes from './gallery.mjs';
import { GALLERIES, findGallery } from './gallery-fixtures.mjs';
import { makeImage } from './gallery-images.mjs';

let server;
let baseUrl;

before(async () => {
  const app = express();
  galleryRoutes(app);
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

const IMAGE_MAGICS = {
  png: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a],
  jpg: [0xff, 0xd8, 0xff],
  gif: [0x47, 0x49, 0x46, 0x38, 0x39, 0x61], // GIF89a
};

function magicBytes(buf, n) {
  return [...buf.subarray(0, n)];
}

function parseGidTokenFromUrl(url) {
  const path = new URL(url).pathname;
  const m = /^\/g\/(\d+)\/([a-f0-9]+)\/?$/.exec(path);
  assert.ok(m, `url ${url} does not look like a gallery detail URL`);
  return { gid: Number(m[1]), token: m[2] };
}

function walkVersions(startGid) {
  const chain = [];
  let current = findGallery(startGid);
  let hops = 0;
  while (current) {
    chain.push(current.gid);
    const next = current.newVersions?.[0];
    if (!next) {
      break;
    }
    assert.ok(hops++ < 20, `newVersions chain too long, possible cycle at gid ${current.gid}`);
    current = findGallery(parseGidTokenFromUrl(next.url).gid);
  }
  return chain;
}

// ---------------------------------------------------------------- detail pages

test('GET /g/1001/aaa1111111/ returns detail page (gid script, title, Length, #gnd, v2 link)', async () => {
  const res = await fetch(`${baseUrl}/g/1001/aaa1111111/`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /^text\/html/);
  const body = await res.text();
  assert.ok(body.includes('var gid = 1001'), 'gid script');
  assert.ok(body.includes('Test Gallery Alpha'), 'title');
  assert.ok(body.includes('Length:'), 'info table Length row');
  assert.ok(body.includes('5 pages'), 'page count');
  assert.ok(body.includes('id="gnd"'), 'new-version list');
  assert.ok(body.includes('/g/1002/bbb2222222/'), 'link to v2 gallery');
});

test('GET /g/1002/bbb2222222/ chains to /g/1003/ccc3333333/', async () => {
  const res = await fetch(`${baseUrl}/g/1002/bbb2222222/`);
  assert.equal(res.status, 200);
  const body = await res.text();
  assert.ok(body.includes('/g/1003/ccc3333333/'), 'link to v3 gallery');
});

test('GET /g/1003/ccc3333333/ has no href links inside div#gnd', async () => {
  const res = await fetch(`${baseUrl}/g/1003/ccc3333333/`);
  assert.equal(res.status, 200);
  const body = await res.text();
  const m = body.match(/<div id="gnd">([\s\S]*?)<\/div>/);
  assert.ok(m, 'div#gnd present');
  assert.ok(!m[1].includes('<a '), 'no link inside #gnd');
});

// ---------------------------------------------------------------- image page

test('GET /s/aaa1111111/1001-1 returns image page with <img> and showkey', async () => {
  const res = await fetch(`${baseUrl}/s/aaa1111111/1001-1`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /^text\/html/);
  const body = await res.text();
  assert.ok(body.includes('<img'), 'has img tag');
  assert.ok(body.includes('/image/1001/1'), 'img src points at raw image');
  assert.ok(body.includes('showkey'), 'has showkey script');
});

// ---------------------------------------------------------------- raw images

test('GET /image/1001/1 returns PNG magic bytes', async () => {
  const res = await fetch(`${baseUrl}/image/1001/1`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /^image\//);
  const buf = Buffer.from(await res.arrayBuffer());
  assert.deepEqual(magicBytes(buf, 8), IMAGE_MAGICS.png, 'PNG signature');
});

test('GET /image/1001/1.jpg returns JPEG magic bytes', async () => {
  const res = await fetch(`${baseUrl}/image/1001/1.jpg`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /image\/jpeg/);
  const buf = Buffer.from(await res.arrayBuffer());
  assert.deepEqual(magicBytes(buf, 3), IMAGE_MAGICS.jpg, 'JPEG signature');
});

test('GET /image/1001/1.gif returns GIF magic bytes', async () => {
  const res = await fetch(`${baseUrl}/image/1001/1.gif`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /image\/gif/);
  const buf = Buffer.from(await res.arrayBuffer());
  assert.deepEqual(magicBytes(buf, 6), IMAGE_MAGICS.gif, 'GIF signature');
});

test('makeImage(gid, page, format) produces correct magic bytes for png/jpg/gif', async () => {
  for (const format of ['png', 'jpg', 'gif']) {
    const buf = makeImage(1001, 1, format);
    assert.ok(Buffer.isBuffer(buf), `${format}: returns a Buffer`);
    const magic = IMAGE_MAGICS[format];
    assert.deepEqual(magicBytes(buf, magic.length), magic, `${format} magic bytes`);
  }
});

test('page number is rendered on the image (download/reading verifiable by eye)', async () => {
  // Page "3" of gid 1001 must differ from page "4" (different stripes) and the
  // JPEG for page 3 must decode to a non-trivial image.
  const jpeg3 = Buffer.from(await (await fetch(`${baseUrl}/image/1001/3.jpg`)).arrayBuffer());
  const jpeg4 = Buffer.from(await (await fetch(`${baseUrl}/image/1001/4.jpg`)).arrayBuffer());
  assert.ok(jpeg3.length > 1000, 'page 3 jpeg has real content');
  assert.ok(!jpeg3.equals(jpeg4), 'page 3 and page 4 images differ');
});

// ---------------------------------------------------------------- thumbnails

test('GET /t/1001/cover.jpg returns an image', async () => {
  const res = await fetch(`${baseUrl}/t/1001/cover.jpg`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /^image\//);
});

// ---------------------------------------------------------------- list page

test('GET /?f_search=alpha returns list page containing gallery title', async () => {
  const res = await fetch(`${baseUrl}/?f_search=alpha`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /^text\/html/);
  const body = await res.text();
  assert.ok(body.includes('Test Gallery Alpha'), 'gallery title in list');
});

// ---------------------------------------------------------------- 404

test('GET /g/9999/zzzz/ returns 404', async () => {
  const res = await fetch(`${baseUrl}/g/9999/zzzz/`);
  assert.equal(res.status, 404);
});

// ---------------------------------------------------------------- fixtures

test('fixtures: GALLERIES is non-empty with valid tokens and resolvable newVersions', () => {
  assert.ok(Array.isArray(GALLERIES) && GALLERIES.length > 0, 'GALLERIES non-empty');
  for (const g of GALLERIES) {
    assert.match(g.token, /^[a-f0-9]+$/, `gid ${g.gid}: token "${g.token}" is hex`);
    for (const v of g.newVersions ?? []) {
      const { gid, token } = parseGidTokenFromUrl(v.url);
      const target = findGallery(gid);
      assert.ok(target, `gid ${g.gid}: newVersions target ${gid} exists in GALLERIES`);
      assert.equal(target.token, token, `gid ${g.gid}: newVersions token matches target`);
    }
  }
});

// ---------------------------------------------------------------- chain

test('version chain walks from 1001 to 1003; 1003 has no newVersions', () => {
  const chain = walkVersions(1001);
  assert.ok(chain.includes(1003), `chain ${chain.join(' -> ')} reaches 1003`);
  const last = findGallery(1003);
  assert.ok(last, '1003 exists');
  assert.ok(!last.newVersions || last.newVersions.length === 0, '1003 has no newVersions');
});
