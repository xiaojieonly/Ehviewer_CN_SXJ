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

// --------------------------- list query oracle (search v1.1 extended params)
//
// The mock site implements the frozen contract semantics of the site URL
// params (contracts/openapi.yaml GET /api/v1/gallery/search) so backend and
// E2E runs can verify filtering/sorting against a known oracle.

/** Extracts gallery gids from a list page in row order. */
async function gidsFor(query) {
  const res = await fetch(`${baseUrl}/${query}`);
  assert.equal(res.status, 200);
  const body = await res.text();
  return [...body.matchAll(/\/g\/(\d+)\//g)].map((m) => Number(m[1]));
}

test('list oracle: no params returns every fixture in default order', async () => {
  assert.deepEqual(await gidsFor(''), [1001, 1002, 1003, 2001, 2002, 3001, 3002]);
});

test('list oracle: f_cats is an exclusion bitmask (f_cats=2 drops Doujinshi)', async () => {
  assert.deepEqual(await gidsFor('?f_cats=2'), [2001, 2002]);
});

test('list oracle: f_cats excludes multiple categories by bit union', async () => {
  // 0x2 (Doujinshi) | 0x4 (Manga) -> only the Western gallery survives
  assert.deepEqual(await gidsFor('?f_cats=6'), [2002]);
});

test('list oracle: f_order=1 sorts by posted time desc', async () => {
  assert.deepEqual(await gidsFor('?f_order=1'), [3002, 3001, 2002, 2001, 1003, 1002, 1001]);
});

test('list oracle: f_order=2 sorts by rating desc', async () => {
  assert.deepEqual(await gidsFor('?f_order=2'), [1003, 1002, 1001, 3002, 3001, 2001, 2002]);
});

test('list oracle: f_order=3 sorts by title asc', async () => {
  assert.deepEqual(await gidsFor('?f_order=3'), [3001, 3002, 2001, 2002, 1001, 1002, 1003]);
});

test('list oracle: f_order=0 keeps the default order', async () => {
  assert.deepEqual(await gidsFor('?f_order=0'), [1001, 1002, 1003, 2001, 2002, 3001, 3002]);
});

test('list oracle: f_sp=on&f_spf=N keeps galleries with at least N pages', async () => {
  assert.deepEqual(await gidsFor('?f_sp=on&f_spf=5'), [1001, 1002, 1003, 2002]);
});

test('list oracle: f_sp=on&f_spt=N keeps galleries with at most N pages', async () => {
  assert.deepEqual(await gidsFor('?f_sp=on&f_spt=4'), [2001, 3001, 3002]);
});

test('list oracle: f_sp with both bounds keeps the inclusive range', async () => {
  assert.deepEqual(await gidsFor('?f_sp=on&f_spf=4&f_spt=6'), [1001, 1002, 2001, 3002]);
});

test('list oracle: f_sp=on without bounds filters nothing', async () => {
  assert.deepEqual(await gidsFor('?f_sp=on'), [1001, 1002, 1003, 2001, 2002, 3001, 3002]);
});

test('list oracle: f_sr=on&f_srdd=N keeps rating >= N (inclusive)', async () => {
  assert.deepEqual(await gidsFor('?f_sr=on&f_srdd=4.5'), [1001, 1002, 1003]);
});

test('list oracle: f_sr without f_srdd filters nothing', async () => {
  assert.deepEqual(await gidsFor('?f_sr=on'), [1001, 1002, 1003, 2001, 2002, 3001, 3002]);
});

// W3 R4-10: higher AdvanceSearchTable bits reach the site oracle.
test('list oracle: f_sto=on keeps only galleries with torrents', async () => {
  // Fixtures 1003 and 2002 are the only rows with torrent entries.
  assert.deepEqual(await gidsFor('?f_sto=on'), [1003, 2002]);
});

test('list oracle: f_sto combines with other filters (intersection)', async () => {
  // Torrent-bearing rows ∩ rating >= 4.5 → only 1003 (2002 rates lower).
  assert.deepEqual(await gidsFor('?f_sto=on&f_sr=on&f_srdd=4.5'), [1003]);
});

test('list oracle: remaining higher bits are accepted no-ops (no 400, full list)', async () => {
  // f_sdt1/f_sdt2/f_sh/f_sfl/f_sfu/f_sft have no fixture-backed oracle and
  // must simply be accepted (Tier-2 passthrough URLs never 400).
  const res = await fetch(
    `${baseUrl}/?f_search=alpha&advsearch=1&f_sdt1=on&f_sdt2=on&f_sh=on&f_sfl=on&f_sfu=on&f_sft=on`,
  );
  assert.equal(res.status, 200);
});

test('list oracle: f_srdd without f_sr=on filters nothing', async () => {
  assert.deepEqual(await gidsFor('?f_srdd=5'), [1001, 1002, 1003, 2001, 2002, 3001, 3002]);
});

test('list oracle: advsearch f_sname=on matches the gallery name only', async () => {
  // "revised" appears in the 3002 title only (not in any description)
  assert.deepEqual(await gidsFor('?advsearch=1&f_sname=on&f_search=revised'), [3002]);
});

test('list oracle: advsearch f_stags=on matches tags only', async () => {
  // "ponytail" is a tag of 2001 only
  assert.deepEqual(await gidsFor('?advsearch=1&f_stags=on&f_search=ponytail'), [2001]);
});

test('list oracle: advsearch f_sdesc=on matches descriptions only', async () => {
  // "commissioned" appears in the 2002 description only (its tag is the
  // shorter "commission", which must not match)
  assert.deepEqual(await gidsFor('?advsearch=1&f_sdesc=on&f_search=commissioned'), [2002]);
});

test('list oracle: advsearch f_storr=on matches torrent filenames only', async () => {
  // "alpha_final_archive" is a torrent filename of 1003 only
  assert.deepEqual(await gidsFor('?advsearch=1&f_storr=on&f_search=alpha_final_archive'), [1003]);
});

test('list oracle: a single scope does not leak into other scopes', async () => {
  // "commissioned" exists only in a description, so name scope finds nothing
  assert.deepEqual(await gidsFor('?advsearch=1&f_sname=on&f_search=commissioned'), []);
});

test('list oracle: multiple scope flags OR together', async () => {
  // name hits 3002 ("revised" in title), tags hits 2001 ("ponytail")
  assert.deepEqual(
    await gidsFor('?advsearch=1&f_sname=on&f_stags=on&f_search=e'),
    await gidsFor('?advsearch=1&f_sname=on&f_stags=on&f_search=E'),
    'keyword match is case-insensitive'
  );
  assert.deepEqual(await gidsFor('?advsearch=1&f_sname=on&f_stags=on&f_search=ponytail'), [2001]);
});

test('list oracle: f_search without advsearch uses default name+tags scope', async () => {
  assert.deepEqual(await gidsFor('?f_search=ponytail'), [2001]);
  assert.deepEqual(await gidsFor('?f_search=revised'), [3002]);
});

test('list oracle: advsearch=1 with no scope flag falls back to default scope', async () => {
  assert.deepEqual(await gidsFor('?advsearch=1&f_search=revised'), [3002]);
});

test('list oracle: combined f_cats + f_sr + f_order', async () => {
  // drop Doujinshi, rating >= 4, posted desc -> only 2001 survives
  assert.deepEqual(await gidsFor('?f_cats=2&f_sr=on&f_srdd=4&f_order=1'), [2001]);
});

test('list oracle: keyword + page-count bound combine', async () => {
  assert.deepEqual(await gidsFor('?f_search=alpha&f_sp=on&f_spt=6'), [1001, 1002]);
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
