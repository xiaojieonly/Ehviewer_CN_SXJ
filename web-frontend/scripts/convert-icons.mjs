#!/usr/bin/env node
/**
 * convert-icons.mjs
 *
 * Converts Android VectorDrawable XML files (v_*.xml) to SVG.
 * Resolves @string/ path-data references, @color/ and ?attr/ color references,
 * and @dimen/ dimension references from the Android resource system.
 *
 * Usage: node web-frontend/scripts/convert-icons.mjs
 *
 * No external dependencies — uses only Node.js built-in modules.
 */

import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync } from 'fs';
import { join, basename, resolve } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Project root (two levels up from web-frontend/scripts/)
const PROJECT_ROOT = resolve(__dirname, '..', '..');

// Source directories
const DRAWABLE_DIR = join(PROJECT_ROOT, 'app/src/main/res/drawable');
const DRAWABLE_V21_DIR = join(PROJECT_ROOT, 'app/src/main/res/drawable-v21');
const VALUES_DIR = join(PROJECT_ROOT, 'app/src/main/res/values');

// Output directory
const OUTPUT_DIR = join(PROJECT_ROOT, 'assets/icons');

// ---------------------------------------------------------------------------
// Resource resolution: parse Android resource XML files into lookup maps
// ---------------------------------------------------------------------------

/**
 * Parse an Android values XML file and extract <string>, <color>, <dimen> entries.
 * Returns a Map of name → value.
 */
function parseValuesResource(filePath, tag) {
  const map = new Map();
  if (!existsSync(filePath)) return map;
  const xml = readFileSync(filePath, 'utf-8');
  // Match <string name="...">...</string>, <color name="...">...</color>, etc.
  const re = new RegExp(`<${tag}\\s+name="([^"]+)"[^>]*>([^<]*)</${tag}>`, 'g');
  let m;
  while ((m = re.exec(xml)) !== null) {
    map.set(m[1], m[2].trim());
  }
  return map;
}

// Build resource maps
const stringResources = parseValuesResource(join(VALUES_DIR, 'pathdata.xml'), 'string');
const colorResources = parseValuesResource(join(VALUES_DIR, 'colors.xml'), 'color');
const dimenResources = parseValuesResource(join(VALUES_DIR, 'dimens.xml'), 'dimen');

// ---------------------------------------------------------------------------
// Color conversion helpers
// ---------------------------------------------------------------------------

/** Known Android framework colors */
const ANDROID_FRAMEWORK_COLORS = {
  'black': '#ff000000',
  'white': '#ffffffff',
  'transparent': '#00000000',
};

/**
 * Resolve an Android color value to { color, opacity }.
 *
 * Handles:
 *  - ?attr/... and ?android:attr/... → currentColor
 *  - @color/name → look up in colorResources
 *  - @android:color/name → framework color
 *  - #AARRGGBB or #RRGGBB → hex (extract alpha if AA != FF)
 *
 * Returns { color: string, opacity: number|null }
 */
function resolveColor(value) {
  if (!value) return { color: null, opacity: null };

  // Theme attribute references → currentColor
  if (value.startsWith('?attr/') || value.startsWith('?android:attr/')) {
    return { color: 'currentColor', opacity: null };
  }

  // @color/ resource reference
  if (value.startsWith('@color/')) {
    const name = value.slice('@color/'.length);
    const resolved = colorResources.get(name);
    if (resolved) return parseHexColor(resolved);
    // Fallback: use currentColor if we can't resolve
    console.warn(`  ⚠ Unresolved color resource: ${value}`);
    return { color: 'currentColor', opacity: null };
  }

  // @android:color/ framework reference
  if (value.startsWith('@android:color/')) {
    const name = value.slice('@android:color/'.length);
    const resolved = ANDROID_FRAMEWORK_COLORS[name];
    if (resolved) return parseHexColor(resolved);
    console.warn(`  ⚠ Unresolved android framework color: ${value}`);
    return { color: 'currentColor', opacity: null };
  }

  // Direct hex color
  if (value.startsWith('#')) {
    return parseHexColor(value);
  }

  console.warn(`  ⚠ Unknown color format: ${value}`);
  return { color: 'currentColor', opacity: null };
}

/**
 * Parse a hex color string (#RGB, #RRGGBB, #AARRGGBB) into { color, opacity }.
 * Android uses #AARRGGBB; SVG uses #RRGGBB + fill-opacity.
 */
function parseHexColor(hex) {
  hex = hex.replace(/^#/, '');

  if (hex.length === 8) {
    // #AARRGGBB
    const aa = hex.slice(0, 2);
    const rr = hex.slice(2, 4);
    const gg = hex.slice(4, 6);
    const bb = hex.slice(6, 8);
    const alpha = parseInt(aa, 16) / 255;
    if (alpha >= 0.999) {
      return { color: `#${rr}${gg}${bb}`, opacity: null };
    }
    return { color: `#${rr}${gg}${bb}`, opacity: Math.round(alpha * 1000) / 1000 };
  }

  if (hex.length === 6) {
    return { color: `#${hex}`, opacity: null };
  }

  if (hex.length === 3) {
    // #RGB → #RRGGBB
    const r = hex[0], g = hex[1], b = hex[2];
    return { color: `#${r}${r}${g}${g}${b}${b}`, opacity: null };
  }

  return { color: `#${hex}`, opacity: null };
}

// ---------------------------------------------------------------------------
// Dimension resolution
// ---------------------------------------------------------------------------

/**
 * Resolve an Android dimension value to a plain number string.
 * Handles: "24dp" → "24", "@dimen/name" → look up, plain number.
 */
function resolveDimension(value) {
  if (!value) return null;

  if (value.startsWith('@dimen/')) {
    const name = value.slice('@dimen/'.length);
    const resolved = dimenResources.get(name);
    if (resolved) return resolved.replace(/dp$/i, '');
    console.warn(`  ⚠ Unresolved dimen resource: ${value}`);
    return '24'; // sensible default
  }

  // Strip dp/sp suffix
  return value.replace(/(dp|sp)$/i, '');
}

// ---------------------------------------------------------------------------
// Path data resolution
// ---------------------------------------------------------------------------

/**
 * Resolve pathData value: either inline or @string/ reference.
 */
function resolvePathData(value) {
  if (!value) return null;

  if (value.startsWith('@string/')) {
    const name = value.slice('@string/'.length);
    const resolved = stringResources.get(name);
    if (resolved) return resolved;
    console.warn(`  ⚠ Unresolved string resource for pathData: ${value}`);
    return null;
  }

  return value;
}

// ---------------------------------------------------------------------------
// XML attribute extraction (regex-based, sufficient for VectorDrawable)
// ---------------------------------------------------------------------------

/**
 * Extract an android-namespaced attribute from an XML element string.
 */
function getAndroidAttr(elementStr, attrName) {
  // Match android:attrName="value"
  const re = new RegExp(`android:${attrName}\\s*=\\s*"([^"]*)"`, 'i');
  const m = elementStr.match(re);
  return m ? m[1] : null;
}

/**
 * Extract all <path .../> or <path ...>...</path> elements from XML.
 */
function extractPathElements(xml) {
  const paths = [];
  // Self-closing <path ... />
  const selfClosingRe = /<path\b([^>]*?)\/>/g;
  let m;
  while ((m = selfClosingRe.exec(xml)) !== null) {
    paths.push(m[1]);
  }
  // Open/close <path ...>...</path> (less common but handle it)
  const pairedRe = /<path\b([^>]*?)>([\s\S]*?)<\/path>/g;
  while ((m = pairedRe.exec(xml)) !== null) {
    paths.push(m[1]);
  }
  return paths;
}

/**
 * Extract the <vector ...> opening tag attributes.
 */
function extractVectorTag(xml) {
  const m = xml.match(/<vector\b([^>]*?)>/s);
  return m ? m[1] : null;
}

// ---------------------------------------------------------------------------
// SVG generation
// ---------------------------------------------------------------------------

/**
 * Convert a single VectorDrawable XML string to SVG.
 * Returns { svg, width, height } or null on failure.
 */
function convertVectorToSvg(xml, fileName) {
  const vectorAttrs = extractVectorTag(xml);
  if (!vectorAttrs) {
    console.warn(`  ✗ No <vector> element found in ${fileName}`);
    return null;
  }

  // Vector dimensions
  const rawWidth = getAndroidAttr(vectorAttrs, 'width');
  const rawHeight = getAndroidAttr(vectorAttrs, 'height');
  const viewportWidth = getAndroidAttr(vectorAttrs, 'viewportWidth') || '24';
  const viewportHeight = getAndroidAttr(vectorAttrs, 'viewportHeight') || '24';

  const width = resolveDimension(rawWidth) || '24';
  const height = resolveDimension(rawHeight) || '24';

  // Parse viewport as float for the viewBox
  const vbW = parseFloat(viewportWidth);
  const vbH = parseFloat(viewportHeight);

  // Extract all path elements
  const pathElements = extractPathElements(xml);
  if (pathElements.length === 0) {
    console.warn(`  ✗ No <path> elements found in ${fileName}`);
    return null;
  }

  // Build SVG path elements
  const svgPaths = [];
  for (const pathAttrs of pathElements) {
    const pathData = resolvePathData(getAndroidAttr(pathAttrs, 'pathData'));
    if (!pathData) {
      console.warn(`  ⚠ Skipping path with unresolvable pathData in ${fileName}`);
      continue;
    }

    const attrs = [];

    // d attribute (path data) — preserve exactly
    attrs.push(`d="${escapeXmlAttr(pathData)}"`);

    // Fill color
    const fillColorRaw = getAndroidAttr(pathAttrs, 'fillColor');
    if (fillColorRaw) {
      const { color, opacity } = resolveColor(fillColorRaw);
      if (color) attrs.push(`fill="${color}"`);
      if (opacity !== null) attrs.push(`fill-opacity="${opacity}"`);
    }

    // Fill alpha (android:fillAlpha)
    const fillAlpha = getAndroidAttr(pathAttrs, 'fillAlpha');
    if (fillAlpha) {
      attrs.push(`fill-opacity="${fillAlpha}"`);
    }

    // Stroke color
    const strokeColorRaw = getAndroidAttr(pathAttrs, 'strokeColor');
    if (strokeColorRaw) {
      const { color, opacity } = resolveColor(strokeColorRaw);
      if (color) attrs.push(`stroke="${color}"`);
      if (opacity !== null) attrs.push(`stroke-opacity="${opacity}"`);
    }

    // Stroke alpha
    const strokeAlpha = getAndroidAttr(pathAttrs, 'strokeAlpha');
    if (strokeAlpha) {
      attrs.push(`stroke-opacity="${strokeAlpha}"`);
    }

    // Stroke width
    const strokeWidth = getAndroidAttr(pathAttrs, 'strokeWidth');
    if (strokeWidth) {
      attrs.push(`stroke-width="${strokeWidth}"`);
    }

    // Stroke line cap
    const strokeLineCap = getAndroidAttr(pathAttrs, 'strokeLineCap');
    if (strokeLineCap) {
      attrs.push(`stroke-linecap="${strokeLineCap}"`);
    }

    // Stroke line join
    const strokeLineJoin = getAndroidAttr(pathAttrs, 'strokeLineJoin');
    if (strokeLineJoin) {
      attrs.push(`stroke-linejoin="${strokeLineJoin}"`);
    }

    // Fill rule (android:fillType="evenOdd" → fill-rule="evenodd")
    const fillType = getAndroidAttr(pathAttrs, 'fillType');
    if (fillType) {
      const svgFillRule = fillType === 'evenOdd' ? 'evenodd' : 'nonzero';
      attrs.push(`fill-rule="${svgFillRule}"`);
    }

    svgPaths.push(`  <path ${attrs.join(' ')}/>`);
  }

  if (svgPaths.length === 0) {
    console.warn(`  ✗ No convertible paths in ${fileName}`);
    return null;
  }

  // Format dimensions: drop trailing .0
  const fmtW = formatNum(parseFloat(width));
  const fmtH = formatNum(parseFloat(height));
  const fmtVbW = formatNum(vbW);
  const fmtVbH = formatNum(vbH);

  const svg = [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${fmtW}" height="${fmtH}" viewBox="0 0 ${fmtVbW} ${fmtVbH}">`,
    ...svgPaths,
    `</svg>`,
    ``,
  ].join('\n');

  return { svg, width: fmtW, height: fmtH };
}

/** Format a number: drop unnecessary trailing zeros / decimal point. */
function formatNum(n) {
  return Number.isInteger(n) ? String(n) : String(n);
}

/** Escape special characters for use inside an XML attribute value. */
function escapeXmlAttr(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

function main() {
  console.log('🔄 Android VectorDrawable → SVG Converter\n');

  // Ensure output directory exists
  mkdirSync(OUTPUT_DIR, { recursive: true });

  // Collect source files
  const sourceFiles = [];

  // drawable/v_*.xml
  if (existsSync(DRAWABLE_DIR)) {
    for (const f of readdirSync(DRAWABLE_DIR).sort()) {
      if (/^v_.*\.xml$/.test(f)) {
        sourceFiles.push({ absPath: join(DRAWABLE_DIR, f), relSource: `drawable/${f}` });
      }
    }
  }

  // drawable-v21/v_*.xml
  if (existsSync(DRAWABLE_V21_DIR)) {
    for (const f of readdirSync(DRAWABLE_V21_DIR).sort()) {
      if (/^v_.*\.xml$/.test(f)) {
        sourceFiles.push({ absPath: join(DRAWABLE_V21_DIR, f), relSource: `drawable-v21/${f}` });
      }
    }
  }

  console.log(`Found ${sourceFiles.length} source VectorDrawable files.\n`);

  const manifest = { files: [], total: 0 };
  let converted = 0;
  let failed = 0;
  const failures = [];

  for (const { absPath, relSource } of sourceFiles) {
    const fileName = basename(absPath);
    const svgName = fileName.replace(/\.xml$/, '.svg');

    try {
      const xml = readFileSync(absPath, 'utf-8');
      const result = convertVectorToSvg(xml, fileName);

      if (result) {
        const outPath = join(OUTPUT_DIR, svgName);
        writeFileSync(outPath, result.svg, 'utf-8');
        converted++;

        manifest.files.push({
          source: relSource,
          output: svgName,
          width: parseFloat(result.width),
          height: parseFloat(result.height),
        });

        console.log(`  ✓ ${fileName} → ${svgName}`);
      } else {
        failed++;
        failures.push(fileName);
        console.log(`  ✗ ${fileName} — conversion returned null`);
      }
    } catch (err) {
      failed++;
      failures.push(fileName);
      console.log(`  ✗ ${fileName} — ${err.message}`);
    }
  }

  manifest.total = converted;

  // Write manifest
  const manifestPath = join(OUTPUT_DIR, 'manifest.json');
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf-8');

  // Summary
  console.log(`\n${'='.repeat(50)}`);
  console.log(`📊 Summary:`);
  console.log(`   Total source files: ${sourceFiles.length}`);
  console.log(`   Converted:          ${converted}`);
  console.log(`   Failed:             ${failed}`);
  if (failures.length > 0) {
    console.log(`   Failures:           ${failures.join(', ')}`);
  }
  console.log(`   Output directory:   ${OUTPUT_DIR}`);
  console.log(`   Manifest:           ${manifestPath}`);
  console.log(`${'='.repeat(50)}`);
}

main();
