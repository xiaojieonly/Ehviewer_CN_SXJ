# Responsive Adaptation Strategy

> Contract document — CA3 output
> Source of truth: Android `res/values/*.xml` + `AutoStaggeredGridLayoutManager.java`
> Status: FROZEN (Wave 0 contract — implementation agents read-only)

## Design Principle

The Android app's design language IS the WebUI's design language. Android `dp` units are
density-independent and the app already adapts to different screens via `AutoStaggeredGridLayoutManager`
(column-width-based auto-spanning) and `sw600dp`/`sw720dp` resource qualifiers. The WebUI must
replicate this adaptive behavior equivalently — not hardcode pixel values for a single device.

**Rule**: Fixed design tokens (colors, radii, curves) are replicated exactly. Fluid layout values
(font sizes, spacing, column counts) scale with viewport while preserving the Android design's
proportional relationships.

---

## 1. Fixed Values (Do NOT Change with Viewport)

These are pixel-exact from Android resources and remain constant across all viewport sizes:

| Category | Values | Source |
|----------|--------|--------|
| Brand colors | `#009688`, `#00796b`, `#e040fb`, `#f9a825` | `colors.xml` |
| Category colors (10) | `#f44336`, `#ff9800`, `#fbc02d`, `#4caf50`, `#8bc34a`, `#2196f3`, `#3f51b5`, `#9c27b0`, `#9575cd`, `#f06292` | `colors.xml` |
| Grey scale (35 steps) | `#080808` → `#f5f5f5` | `colors.xml` |
| Theme bg/surface/divider | Light `#ffffff`/`#f5f5f5`/`rgba(0,0,0,0.125)`, Dark `#323232`/`#3a3a3a`/`rgba(255,255,255,0.125)`, Black `#000000`/`#191919`/`rgba(255,255,255,0.125)` | `themes.xml` |
| Card border-radius | `2px` | `styles.xml` CardView.Normal |
| Card elevation/shadow | `2px` | `styles.xml` CardView.Normal |
| Icon paths & colors | All Material vector icons (24dp standard) | `drawable/v_*.xml` |
| Animation curves | `cubic-bezier(0.165, 0.84, 0.44, 1)` (quart), `cubic-bezier(0.23, 1, 0.32, 1)` (quint) | `anim/decelerate_quart.xml`, `anim/decelerate_quint.xml` |
| Animation durations | Opacity `200ms`, translate `350ms` | `anim/scene_open_enter.xml` |
| Rating star size/interval | `16px` / `1px` | `dimens.xml` |
| FAB sizes | Primary `56px`, mini `40px` | `dimens.xml` |
| Spinner sizes | Small `16px`, default `48px`, large `76px` | `styles.xml` ProgressView |
| Thumbnail aspect ratio clamp | min `0.333`, max `1.333` | `FixedThumb` attrs in layouts |
| SeekBar panel | Height `48px`, page label width `32px`, bg dark `#424242` / black `#212121` | `activity_gallery.xml`, `colors.xml` |

---

## 2. Fluid Values (Scale with Viewport)

### 2.1 Font Sizes — `clamp()` Strategy

Android `sp` values map 1:1 to CSS `px` as the *ideal* size. On small viewports, scale down;
on large viewports, optionally scale up. Use `clamp(min, ideal, max)`:

| Token | Android sp | CSS ideal | clamp() |
|-------|-----------|-----------|---------|
| `--text-super-large` | 24sp | 24px | `clamp(20px, 24px, 28px)` |
| `--text-large` | 22sp | 22px | `clamp(18px, 22px, 26px)` |
| `--text-little-large` | 20sp | 20px | `clamp(17px, 20px, 24px)` |
| `--text-medium` | 18sp | 18px | `clamp(16px, 18px, 22px)` |
| `--text-little-small` | 16sp | 16px | `clamp(14px, 16px, 18px)` |
| `--text-small` | 14sp | 14px | `clamp(13px, 14px, 16px)` |
| `--text-super-small` | 12sp | 12px | `clamp(11px, 12px, 14px)` |

**Implementation note**: The tokens.css declares static values (exact Android sp). Components
that need fluid behavior apply `clamp()` at the component level using these tokens as the ideal.
This keeps tokens pure and lets components opt into fluidity.

### 2.2 Spacing & Thumbnail Sizes — Viewport Breakpoint Scaling

Spacing values step up at breakpoints (matching Android resource qualifiers):

| Token | Default (< 600px) | ≥ 600px (sw600dp) |
|-------|-------------------|--------------------|
| `--keyline-margin` | 16px | 24px |
| `--single-max-width` | 260px | 480px |
| `--gallery-list-interval` | 0px | 4px |
| `--gallery-list-margin-h` | 4px | 20px |
| `--gallery-grid-margin-h` | 4px | 20px |
| `--gallery-search-bar-margin-h` | 6px | 20px |
| `--search-layout-interval` | 0px | 4px |
| `--search-layout-margin-h` | 4px | 20px |

These are already implemented in `tokens.css` via `@media (min-width: 600px)`.

---

## 3. Breakpoints

Derived from Android's own adaptive mechanisms:

| Breakpoint | CSS min-width | Android source | Purpose |
|------------|--------------|----------------|---------|
| `xs` | 0 | Default resources | Phone portrait, single-column |
| `sm` | 600px | `values-sw600dp/` qualifier | Tablet portrait, wider margins, multi-column |
| `lg` | 720px | `values-sw720dp/` qualifier | Large tablet / landscape |
| Auto-column | dynamic | `AutoStaggeredGridLayoutManager` | Column count = `floor(availableWidth / columnWidth)` |

### AutoStaggeredGrid Column-Width Logic

The Android layout manager computes span count as:

```
spanCount = max(1, floor(totalSpace / columnSize))    // STRATEGY_MIN_SIZE
```

Where `columnSize` comes from user-selected detail size setting:

| Mode | Size setting | columnWidth | Source dimen |
|------|-------------|-------------|--------------|
| List | Long | 480dp | `gallery_list_column_width_long` |
| List | Short | 320dp | `gallery_list_column_width_short` |
| Grid | Large | 160dp | `gallery_grid_column_width_large` |
| Grid | Middle | 120dp | `gallery_grid_column_width_middle` |
| Grid | Small | 80dp | `gallery_grid_column_width_small` |

**CSS equivalent**: Use CSS multi-column or CSS Grid with `auto-fill`:

```css
/* Masonry / staggered grid equivalent */
.gallery-grid {
  column-width: var(--column-width-grid-middle); /* 120px */
  column-gap: var(--gallery-grid-interval);
}

/* Or CSS Grid auto-fill */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--column-width-grid-middle), 1fr));
  gap: var(--gallery-grid-margin-v) var(--gallery-grid-interval);
}
```

The column count is NEVER hardcoded — it auto-derives from available width ÷ column-width,
exactly matching Android's `AutoStaggeredGridLayoutManager.getSpanCountForMinSize()`.

---

## 4. Grid Auto-Column Implementation

### CSS `column-width` Equivalent

Android's `AutoStaggeredGridLayoutManager` with `STRATEGY_MIN_SIZE`:
- Input: `totalSpace` (container width minus padding), `columnSize` (minimum column width)
- Output: `spanCount = max(1, totalSpace / columnSize)`

Web implementation:

```css
/* Option A: CSS Grid (preferred for card grids) */
.auto-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--column-width), 100%), 1fr));
}

/* Option B: CSS Multi-column (for true masonry/staggered) */
.auto-masonry {
  columns: var(--column-width);
  column-gap: var(--gallery-grid-interval);
}
```

Where `--column-width` is set per mode:
- List mode long: `var(--gallery-list-column-width-long)` = 480px
- List mode short: `var(--gallery-list-column-width-short)` = 320px
- Grid large: `var(--gallery-grid-column-width-large)` = 160px
- Grid middle: `var(--gallery-grid-column-width-middle)` = 120px
- Grid small: `var(--gallery-grid-column-width-small)` = 80px

### Resulting Column Counts (Reference)

| Viewport width | Grid middle (120px) | Grid large (160px) | List short (320px) |
|---------------|--------------------|--------------------|---------------------|
| 375px (phone) | 3 | 2 | 1 |
| 768px (iPad portrait) | 6 | 4 | 2 |
| 1024px (iPad landscape) | 8 | 6 | 3 |
| 1440px (desktop) | 12 | 9 | 4 |

These match what Android produces on equivalent screen densities.

---

## 5. Thumbnail Containers

### Aspect Ratio & Object-Fit

Android's `FixedThumb` / `TileThumb` widgets enforce aspect ratio clamping:

```xml
<!-- item_gallery_list.xml -->
<com.hippo.ehviewer.widget.FixedThumb
    app:minAspect="0.333"
    app:maxAspect="1.333" />
```

Web equivalent:

```css
.thumb-container {
  aspect-ratio: 2 / 3;  /* Default for list thumbnails (80×120 = 2:3) */
  overflow: hidden;
}

.thumb-container img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Aspect ratio clamping: if source image ratio falls outside 0.333–1.333,
   the container still maintains 2:3 and object-fit:cover crops */
.thumb-container--clamped {
  aspect-ratio: clamp(0.333, var(--natural-aspect, 0.667), 1.333);
}
```

### Thumbnail Sizes by Context

| Context | Width | Height | Ratio | Source |
|---------|-------|--------|-------|--------|
| List card | 80px | 120px | 2:3 | `gallery_list_thumb_width/height` |
| Detail header | 128px | 192px | 2:3 | `gallery_detail_thumb_width/height` |
| Grid tile | fills column | auto (2:3) | 2:3 | Column width × 1.5 |
| Preview grid | fills column | auto | 0.5–0.8 | `item_gallery_preview.xml` minAspect/maxAspect |

---

## 6. Reader Dual-Page Mode

### Trigger Condition

Android's `GalleryActivity` activates dual-page (two pages side-by-side) when:
- Device is in **landscape orientation**
- (Implicitly: viewport is wider than tall)

Web equivalent — triggered by viewport aspect ratio, NOT device type:

```css
/* Dual-page layout when viewport is landscape */
@media (orientation: landscape) {
  .reader-container {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}

/* Or more precisely, using aspect-ratio media query */
@media (min-aspect-ratio: 1/1) {
  .reader-container {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}
```

### Behavior Rules

1. **Portrait** (`orientation: portrait` or `aspect-ratio < 1`): Single page, full width
2. **Landscape** (`orientation: landscape` or `aspect-ratio ≥ 1`): Dual page side-by-side
3. User preference can override (settings: force single / force dual / auto)
4. In dual-page mode, page pairs are (2,3), (4,5), etc. — first page is alone (cover)
5. RTL reading direction reverses page order (right-to-left)

### Reader Image Sizing

```css
.reader-page img {
  max-width: 100%;
  max-height: calc(100vh - var(--toolbar-height) - var(--seekbar-panel-height));
  object-fit: contain;
}
```

---

## 7. Navigation Drawer

### Behavior by Viewport Width

| Viewport | Behavior | Android equivalent |
|----------|----------|-------------------|
| < 600px | Modal overlay (hamburger toggle, scrim backdrop) | DrawerLayout overlay on phones |
| ≥ 600px | Persistent (always visible, content shifts) | DrawerLayout locked open on tablets |

### Implementation

```css
.drawer {
  width: var(--drawer-width);  /* 280px */
  position: fixed;
  top: 0;
  left: 0;
  height: 100%;
  z-index: 100;
  transform: translateX(-100%);
  transition: transform var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.drawer--open {
  transform: translateX(0);
}

@media (min-width: 600px) {
  .drawer {
    transform: translateX(0);  /* Always visible */
    position: sticky;
  }
  .app-content {
    margin-left: var(--drawer-width);
  }
}
```

### Drawer Structure (Fixed Dimensions)

| Element | Size | Source |
|---------|------|--------|
| Drawer width | 280px | `drawer_max_width` |
| Header height | 160px | `nav_header_main.xml` |
| Avatar | 64px circle | Layout |
| Username text | 14px white | `text_small` |
| Menu items | 8 items, single-select group | `nav_drawer_main.xml` |
| Bottom | Quota widget + theme toggle | Layout |

---

## 8. Responsive Images

### `srcset` / `<picture>` Strategy

The server provides on-demand image resizing via `?w={width}` parameter:

```
GET /api/image/{galleryId}/{page}?w={width}
```

Frontend implementation:

```html
<!-- Thumbnail in gallery grid -->
<img
  :src="`/api/image/${id}/0?w=240`"
  :srcset="`
    /api/image/${id}/0?w=120 120w,
    /api/image/${id}/0?w=240 240w,
    /api/image/${id}/0?w=480 480w
  `"
  :sizes="`(min-width: 600px) ${columnWidth}px, 33vw`"
  loading="lazy"
  decoding="async"
/>

<!-- Reader full page -->
<img
  :src="`/api/image/${id}/${page}?w=${containerWidth}`"
  :srcset="`
    /api/image/${id}/${page}?w=${containerWidth} 1x,
    /api/image/${id}/${page}?w=${containerWidth * 2} 2x
  `"
/>
```

### Resolution Selection Logic

| Context | Width hint | DPR consideration |
|---------|-----------|-------------------|
| Grid thumbnail (small 80px col) | `?w=80` | `×dpr` → 160 on Retina |
| Grid thumbnail (middle 120px col) | `?w=120` | `×dpr` → 240 on Retina |
| Grid thumbnail (large 160px col) | `?w=160` | `×dpr` → 320 on Retina |
| List thumbnail (80px fixed) | `?w=80` | `×dpr` |
| Detail header (128px) | `?w=128` | `×dpr` |
| Reader single page | `?w={containerWidth}` | `×dpr` |
| Reader dual page | `?w={containerWidth/2}` | `×dpr` |

### Server Contract

- `?w={width}`: Server scales image to fit within `width` pixels wide, maintaining aspect ratio
- Omitting `?w`: Returns original resolution
- Response includes `Cache-Control` headers for browser/CDN caching
- Supports `Range` requests for progressive loading
- Format negotiation: `Accept: image/webp` → WebP if available, else JPEG/PNG

---

## Summary: What Scales vs. What's Fixed

```
FIXED (exact Android values, all viewports):
├── Colors (brand, category, grey scale, themes)
├── Border-radius (2px cards)
├── Shadow/elevation (2px)
├── Icon SVG paths & colors
├── Animation curves (cubic-bezier) & durations (200/350ms)
├── FAB sizes (56/40px)
├── Rating star size (16px) & interval (1px)
├── Spinner sizes (16/48/76px)
├── Drawer width (280px) & header height (160px)
├── Toolbar height (56px)
├── SeekBar panel (48px height, 32px labels)
└── Aspect ratio clamp (0.333–1.333)

FLUID (scales with viewport):
├── Font sizes → clamp(min, ideal_sp, max)
├── Spacing/margins → step at 600px breakpoint (sw600dp values)
├── Grid column count → auto-fill from column-width
├── Thumbnail container width → fills grid column
├── Drawer mode → modal (<600px) / persistent (≥600px)
├── Reader layout → single (portrait) / dual (landscape)
└── Image resolution → srcset + ?w={width} per container
```

---

## Affected Tasks

| Task ID | What this strategy governs |
|---------|---------------------------|
| CA3 | This document + tokens.css (breakpoints & fluid values) |
| F2 | Thumbnail `aspect-ratio` container, `object-fit: cover` |
| S1 | Auto-column masonry via `column-width`, breakpoint spacing |
| S3 | Dual-page by `orientation`/`aspect-ratio`, `srcset` for reader images |
| B5/CA1 | Image API `?w={width}` parameter |
| H3 | Multi-resolution verification across breakpoints |
