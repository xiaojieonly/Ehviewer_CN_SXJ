// Materialize the per-path gallery image corpus from a small seed pool.
//
// The mock Gallery-Site server (gallery.mjs) references images at fixed URL
// paths derived from gallery-fixtures.mjs:
//   /image/{gid}/{page}.jpg     (full page, used by the image page <img>)
//   /t/{gid}/cover.jpg          (cover thumb, used by detail .gm + list)
//   /t/{gid}/{i}.jpg            (per-page preview thumb, used by #gdt)
// This script copies bytes from a small seed pool (mock-server/assets/seed/*,
// generated via the Bailian CLI, content-irrelevant) into those exact paths so
// every URL the mock needs resolves to a real file. Seed bytes are JPEG, and
// per-path names are .jpg, so extension == bytes (mock may set Content-Type by
// extension safely; sniffing by magic bytes is still recommended, see TODO M-1).
//
// Run:  node mock-server/scripts/gen-corpus.mjs
// Idempotent: re-runs overwrite the per-path tree from the current seed pool.
// The seed pool itself is NOT touched (it is the persisted Bailian output).

import { readFileSync, readdirSync, mkdirSync, copyFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

import { GALLERIES } from '../gallery-fixtures.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSETS = resolve(HERE, '..', 'assets');
const SEED_DIR = join(ASSETS, 'seed');
const IMAGE_DIR = join(ASSETS, 'image');
const THUMB_DIR = join(ASSETS, 't');

// Provenance of the seed pool (recorded into MANIFEST for reproducibility).
const SEED_PROVENANCE = {
  tool: 'bailian-cli (bl image generate)',
  model: 'qwen-image-2.0',
  size: '3:4',
  watermark: false,
  promptExtend: false,
  prompt:
    'abstract colorful vertical page, flat color blocks and geometric shapes, ' +
    'diagonal gradient stripes, NO text NO letters NO words, vibrant palette',
  note:
    'Content is irrelevant; seeds are reused round-robin across all galleries/pages. ' +
    'Seeds stored as JPEG so per-path .jpg matches the bytes.',
};

function loadSeedPool() {
  const files = readdirSync(SEED_DIR)
    .filter((f) => /\.(jpe?g|png|gif)$/i.test(f))
    .sort();
  if (files.length === 0) {
    throw new Error(`No seed images found in ${SEED_DIR}. Generate them first (see TODO C-0).`);
  }
  return files.map((f) => join(SEED_DIR, f));
}

function ensureDir(d) {
  mkdirSync(d, { recursive: true });
}

function pick(pool, gid, page, salt = 0) {
  return pool[((gid + page + salt) % pool.length + pool.length) % pool.length];
}

const pool = loadSeedPool();
let imageCount = 0;
let thumbCount = 0;
const gallerySummary = [];

for (const g of GALLERIES) {
  const gid = Number(g.gid);
  const pages = Number(g.pages);
  const gidImageDir = join(IMAGE_DIR, String(gid));
  const gidThumbDir = join(THUMB_DIR, String(gid));
  ensureDir(gidImageDir);
  ensureDir(gidThumbDir);

  // Cover thumb.
  copyFileSync(pick(pool, gid, 0, 7), join(gidThumbDir, 'cover.jpg'));
  thumbCount += 1;

  for (let page = 1; page <= pages; page++) {
    // Full page image.
    copyFileSync(pick(pool, gid, page, 0), join(gidImageDir, `${page}.jpg`));
    imageCount += 1;
    // Per-page preview thumb (salt differs from the page image so they are not
    // always byte-identical, giving the reader/thumb a bit of variety).
    copyFileSync(pick(pool, gid, page, 3), join(gidThumbDir, `${page}.jpg`));
    thumbCount += 1;
  }

  gallerySummary.push({ gid, pages });
}

const manifest = {
  generatedAt: new Date().toISOString(),
  seedPool: pool.map((p) => p.slice(SEED_DIR.length + 1)),
  seedCount: pool.length,
  galleries: gallerySummary,
  counts: { fullImages: imageCount, thumbs: thumbCount },
  provenance: SEED_PROVENANCE,
};
writeFileSync(join(ASSETS, 'MANIFEST.json'), JSON.stringify(manifest, null, 2) + '\n');

console.log(`seed pool: ${pool.length} file(s)`);
console.log(`galleries: ${gallerySummary.length} -> full images ${imageCount}, thumbs ${thumbCount}`);
console.log(`wrote ${join(ASSETS, 'MANIFEST.json')}`);
