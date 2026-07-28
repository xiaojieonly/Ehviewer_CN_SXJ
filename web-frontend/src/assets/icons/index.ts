/**
 * Icon registry — auto-discovers all SVG files in this directory via Vite glob.
 *
 * Naming convention (derived from Android VectorDrawable filenames):
 *   v_magnify_dark_x24.svg  →  magnify-dark
 *   v_heart_x16.svg         →  heart
 *   v_slider_bubble.svg     →  slider-bubble
 *
 * Rules: strip `v_` prefix, strip `_xNN` size suffix, underscores → hyphens.
 * When two files map to the same name (e.g. x16 / x24 size variants), the
 * larger-size variant wins.
 */

// Eagerly import every SVG in this directory as a raw string.
const svgModules = import.meta.glob('./*.svg', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

/** Strip prefix, size suffix, and convert underscores to hyphens. */
export function deriveIconName(filename: string): string {
  const base = filename
    .replace(/^\.\//, '')   // ./v_foo_x24.svg → v_foo_x24.svg
    .replace(/\.svg$/, '')  // v_foo_x24.svg   → v_foo_x24
  return base
    .replace(/^v_/, '')      // strip v_ prefix
    .replace(/_x\d+$/, '')   // strip _xNN size suffix
    .replace(/_/g, '-')      // underscores → hyphens
}

/** Extract the numeric size from a filename (e.g. `_x24` → 24). Defaults to 0. */
function extractSize(filename: string): number {
  const m = filename.match(/_x(\d+)\.svg$/)
  return m ? parseInt(m[1], 10) : 0
}

// ---------------------------------------------------------------------------
// Build the icon map (name → raw SVG string)
// ---------------------------------------------------------------------------

/** Map of derived icon name → raw SVG markup. */
export const icons: Record<string, string> = {}

/** Map of derived icon name → original filename (for debugging / raw lookup). */
export const iconSources: Record<string, string> = {}

// Track sizes so that on collision the larger variant wins.
const resolvedSizes: Record<string, number> = {}

for (const [path, svg] of Object.entries(svgModules)) {
  const name = deriveIconName(path)
  const size = extractSize(path)

  if (name in icons && resolvedSizes[name] >= size) {
    // Existing entry has equal or larger size — skip.
    continue
  }

  icons[name] = svg
  iconSources[name] = path.replace(/^\.\//, '')
  resolvedSizes[name] = size
}

/** All available icon names, sorted alphabetically. */
export const iconNames: string[] = Object.keys(icons).sort()

/**
 * Raw name mapping — full filename (minus extension) → raw SVG markup.
 * Useful when the derived name loses information (color / size variant).
 */
export const rawIcons: Record<string, string> = {}
for (const [path, svg] of Object.entries(svgModules)) {
  const key = path.replace(/^\.\//, '').replace(/\.svg$/, '')
  rawIcons[key] = svg
}
