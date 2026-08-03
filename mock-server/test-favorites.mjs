// Automated tests for the Gallery Site mock cloud-favorites page (R4-8).
//
// Run with:  node --test test-favorites.mjs   (or: node test-favorites.mjs)
// Exit code 0 = all pass, non-zero = at least one failure.
//
// The server under test is started in-process on a random port. These tests
// assert the DOM contract that the Android FavoritesParser requires (see
// app/.../client/parser/FavoritesParser.java + GalleryListParser.java):
//   - .ido wrapper with EXACTLY 11 .fp elements (10 folders + 1 "fp fps")
//   - each folder row: child(0)=count, child(2)=name
//   - .searchnav <select> with a selected option -> favOrder
//   - gallery list rows (.itg > tr.gtr) parsed by GalleryListParser
//   - empty folder -> literal "No hits found</p>" (pages=0, empty list),
//     NOT an empty .itg (which would throw "No gallery")

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import galleryRoutes from './gallery.mjs';
import { FAVORITE_FOLDERS, FAVORITES, allFavoriteGids, favoriteCount } from './gallery-fixtures.mjs';

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

async function fetchText(path) {
  const res = await fetch(`${baseUrl}${path}`);
  assert.equal(res.status, 200, `GET ${path} -> ${res.status}`);
  assert.match(res.headers.get('content-type') ?? '', /text\/html/, 'content-type is html');
  return res.text();
}

// Count non-overlapping occurrences of a substring.
function count(haystack, needle) {
  let n = 0;
  let i = 0;
  while ((i = haystack.indexOf(needle, i)) !== -1) {
    n += 1;
    i += needle.length;
  }
  return n;
}

// The 10 folder rows use class="fp" (closing quote right after fp); the "all"
// row uses class="fp fps". Counting the exact token keeps them distinct.
function assertFolderBar(html) {
  assert.ok(html.includes('class="ido"'), '.ido wrapper present');
  assert.equal(count(html, 'class="fp"'), 10, 'exactly 10 folder rows');
  assert.equal(count(html, 'class="fp fps"'), 1, 'exactly 1 "fp fps" (All) row');
  for (let i = 0; i < 10; i += 1) {
    const row =
      `<div class="fp"><div>${favoriteCount(i)}</div><div></div>` +
      `<div>${FAVORITE_FOLDERS[i]}</div></div>`;
    assert.ok(html.includes(row), `folder ${i}: count=${favoriteCount(i)} name="${FAVORITE_FOLDERS[i]}"`);
  }
}

test('GET /favorites.php (default) returns the full favorites page', async () => {
  const html = await fetchText('/favorites.php');

  // Not the login wall that FavoritesParser rejects.
  assert.ok(!html.includes('This page requires you to log on.</p>'));

  assertFolderBar(html);

  // Gallery list: every favorited gallery is present as a row + detail link.
  const gids = allFavoriteGids();
  assert.equal(count(html, 'class="gtr"'), gids.length, 'one row per favorited gallery');
  for (const gid of gids) {
    assert.ok(html.includes(`/g/${gid}/`), `gallery ${gid} detail link present`);
  }

  // favOrder: a selected option in the .searchnav select.
  assert.ok(html.includes('class="searchnav"'), '.searchnav present');
  assert.ok(html.includes('value="f" selected="selected"'), 'favOrder select has selected value="f"');

  // Single-page pagination bar.
  assert.ok(html.includes('class="ptt"'), '.ptt pagination present');
});

test('GET /favorites.php?favcat=0 returns folder 0 only', async () => {
  const html = await fetchText('/favorites.php?favcat=0');
  assertFolderBar(html);
  const expected = FAVORITES[0];
  assert.equal(count(html, 'class="gtr"'), expected.length);
  for (const gid of expected) {
    assert.ok(html.includes(`/g/${gid}/`), `folder 0 contains ${gid}`);
  }
  // A gallery that is NOT in folder 0 must not appear.
  assert.ok(!html.includes('/g/2001/'), 'gid 2001 (folder 1) excluded from favcat=0');
});

test('GET /favorites.php?favcat=1 returns folder 1 only', async () => {
  const html = await fetchText('/favorites.php?favcat=1');
  assertFolderBar(html);
  assert.equal(count(html, 'class="gtr"'), FAVORITES[1].length);
  assert.ok(html.includes('/g/2001/'));
});

test('GET /favorites.php?favcat=5 (empty folder) returns "No hits found", no rows', async () => {
  const html = await fetchText('/favorites.php?favcat=5');
  assertFolderBar(html); // folder bar still parses (10 slots + All)
  assert.ok(html.includes('No hits found</p>'), 'empty-folder marker present');
  assert.equal(count(html, 'class="gtr"'), 0, 'no gallery rows in an empty folder');
  // The empty shape must not carry a .ptt bar (parser maps it to pages=0).
  assert.ok(!html.includes('class="ptt"'), 'no .ptt for empty folder');
});

test('GET /favorites.php?favcat=all behaves like the default list', async () => {
  const html = await fetchText('/favorites.php?favcat=all');
  assertFolderBar(html);
  assert.equal(count(html, 'class="gtr"'), allFavoriteGids().length);
});
