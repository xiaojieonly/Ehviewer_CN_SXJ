#!/usr/bin/env node
/**
 * Visual-regression comparator.
 *
 * Diffs every PNG in e2e/baseline/ against the same-named PNG in e2e/actual/
 * using pixelmatch, prints a per-screen diff percentage, writes a diff image
 * per screen into e2e/diff/, and exits non-zero if any screen exceeds the
 * allowed diff threshold (or an actual screenshot is missing / mis-sized).
 *
 * Env knobs:
 *   DIFF_THRESHOLD_PERCENT  max allowed diff % per screen (default 1.0)
 *   PIXELMATCH_THRESHOLD    pixelmatch color tolerance 0..1 (default 0.1)
 *
 * This script is framework-agnostic: it does not need to know the capture
 * matrix — it simply compares whatever baselines exist, by filename.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { PNG } from 'pngjs'
import pixelmatch from 'pixelmatch'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const baselineDir = path.resolve(__dirname, 'baseline')
const actualDir = path.resolve(__dirname, 'actual')
const diffDir = path.resolve(__dirname, 'diff')

const THRESHOLD_PERCENT = Number.parseFloat(process.env.DIFF_THRESHOLD_PERCENT ?? '1.0')
const PIXEL_THRESHOLD = Number.parseFloat(process.env.PIXELMATCH_THRESHOLD ?? '0.1')

if (!Number.isFinite(THRESHOLD_PERCENT) || !Number.isFinite(PIXEL_THRESHOLD)) {
  console.error('Invalid DIFF_THRESHOLD_PERCENT / PIXELMATCH_THRESHOLD — must be numbers.')
  process.exit(2)
}

function readPng(file) {
  return PNG.sync.read(fs.readFileSync(file))
}

function listBaselines() {
  if (!fs.existsSync(baselineDir)) return []
  return fs
    .readdirSync(baselineDir)
    .filter((f) => f.toLowerCase().endsWith('.png'))
    .sort()
}

function main() {
  const baselines = listBaselines()
  if (baselines.length === 0) {
    console.error('No baseline screenshots found in e2e/baseline/.')
    console.error('Generate them first with:  npm run test:visual:update')
    process.exit(2)
  }
  if (!fs.existsSync(actualDir)) {
    console.error('No e2e/actual/ directory — run the capture step first (npm run test:visual).')
    process.exit(2)
  }
  fs.mkdirSync(diffDir, { recursive: true })

  console.log(`Comparing ${baselines.length} baseline screen(s) — threshold ≤ ${THRESHOLD_PERCENT}% (pixelmatch=${PIXEL_THRESHOLD})\n`)

  let passed = 0
  let failed = 0
  let missing = 0

  for (const name of baselines) {
    const baseFile = path.join(baselineDir, name)
    const actFile = path.join(actualDir, name)

    if (!fs.existsSync(actFile)) {
      console.error(`✗ ${name}: MISSING actual screenshot (capture run incomplete?)`)
      missing++
      failed++
      continue
    }

    const base = readPng(baseFile)
    const act = readPng(actFile)

    if (base.width !== act.width || base.height !== act.height) {
      console.error(
        `✗ ${name}: dimension mismatch — baseline ${base.width}x${base.height} vs actual ${act.width}x${act.height}`,
      )
      failed++
      continue
    }

    const { width, height } = base
    const diff = new PNG({ width, height })
    const diffPixels = pixelmatch(base.data, act.data, diff.data, width, height, {
      threshold: PIXEL_THRESHOLD,
    })
    const total = width * height
    const pct = (diffPixels / total) * 100
    fs.writeFileSync(path.join(diffDir, name), PNG.sync.write(diff))

    const ok = pct <= THRESHOLD_PERCENT
    if (ok) passed++
    else failed++
    console.log(`${ok ? '✓' : '✗'} ${name}: ${pct.toFixed(4)}% diff (${diffPixels}/${total} px)`)
  }

  console.log('\n=== Visual Regression Summary ===')
  console.log(`Screens: ${baselines.length}   Passed: ${passed}   Failed: ${failed}   Missing: ${missing}`)
  console.log(`Threshold: ≤ ${THRESHOLD_PERCENT}% diff per screen`)

  if (failed > 0) {
    console.error(`\nVISUAL REGRESSION FAILED: ${failed} screen(s) exceeded threshold or were missing.`)
    console.error(`Diff images written to ${diffDir}`)
    process.exit(1)
  }
  console.log('\nAll screens within threshold. ✅')
}

main()
