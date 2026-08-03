/**
 * searchFilters.ts — pure mapping helpers between the search filter state
 * (`SearchFilters`, `@/api/gallery`), the active-filter chip row, and the
 * `QuickSearchDto` schema (`QuickSearch` in `@/types`, contracts/openapi.yaml).
 *
 * Wave-1 1a (task A5). Everything here is side-effect free so the
 * filter↔query-param and filter↔quick-search mappings can be tested per case.
 *
 * Category semantics follow the frozen contract (`@/types/components.ts`):
 * the `SearchFilters.category` bitmask uses Android `SiteConfig` encoding —
 * a SET bit means the category is EXCLUDED from results (f_cats passthrough),
 * which is the same encoding `QuickSearchDto.category` stores.
 */

import type { QuickSearch } from '@/types'
import type { SearchFilters, SearchSortOrder } from '@/api/gallery'
import type { GalleryCategory } from '@/types/components'
import {
  ADVANCE_SEARCH_BITS,
  CATEGORY_BIT_VALUES,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
} from '@/types/components'

/** One entry of the SearchBar active-filter chip row. */
export interface FilterChip {
  /**
   * Stable chip id — `'sort'`, `'pageMin'`, `'pageMax'`, `'minRating'`,
   * the scope key (`'searchName'` …), or `'category:<bit>'` for a single
   * excluded category. `removeFilterChip` keys on this.
   */
  id: string
  /** Human-readable chip label. */
  label: string
}

/** Sort options — openapi `searchGallery.sort` semantics (0 = default). */
export const SORT_OPTIONS: ReadonlyArray<{ value: SearchSortOrder; label: string }> = [
  { value: 0, label: 'Default order' },
  { value: 1, label: 'Posted time — newest first' },
  { value: 2, label: 'Rating — highest first' },
  { value: 3, label: 'Title — ascending' },
]

/** Minimum-rating options — 0 disables the filter, 2–5 are the thresholds. */
export const MIN_RATING_OPTIONS: ReadonlyArray<{ value: number; label: string }> = [
  { value: 0, label: 'Any rating' },
  { value: 2, label: 'At least 2 stars' },
  { value: 3, label: 'At least 3 stars' },
  { value: 4, label: 'At least 4 stars' },
  { value: 5, label: 'At least 5 stars' },
]

/**
 * The four advanced search-scope switches, mapped 1:1 to their
 * `AdvanceSearchTable` bits so the filter state round-trips through
 * `QuickSearchDto.advanceSearch`.
 */
export const SCOPE_ITEMS: ReadonlyArray<{
  key: 'searchName' | 'searchTags' | 'searchDesc' | 'searchTorrents'
  bit: number
  label: string
}> = [
  { key: 'searchName', bit: ADVANCE_SEARCH_BITS.SNAME, label: 'Gallery name' },
  { key: 'searchTags', bit: ADVANCE_SEARCH_BITS.STAGS, label: 'Gallery tags' },
  { key: 'searchDesc', bit: ADVANCE_SEARCH_BITS.SDESC, label: 'Description' },
  { key: 'searchTorrents', bit: ADVANCE_SEARCH_BITS.STORR, label: 'Torrent filenames' },
]

/** Filter-state key union of the scope + higher switches (chip ids, payload packing). */
export type AdvanceSwitchKey =
  | (typeof SCOPE_ITEMS)[number]['key']
  | (typeof ADVANCED_ITEMS)[number]['key']

/**
 * W3 R4-10: the higher `AdvanceSearchTable` bits, backendized as their own
 * REST params (openapi GET /gallery/search) so the single FilterPanel
 * surface can express the full Android advanced option set.
 */
export const ADVANCED_ITEMS: ReadonlyArray<{
  key:
    | 'searchTorrentsOnly'
    | 'searchLowPowerTags'
    | 'searchDownvotedTags'
    | 'searchExpunged'
    | 'disableLanguageFilter'
    | 'disableUploaderFilter'
    | 'disableTagFilter'
  bit: number
  label: string
}> = [
  { key: 'searchTorrentsOnly', bit: ADVANCE_SEARCH_BITS.STO, label: 'Only with torrents' },
  { key: 'searchLowPowerTags', bit: ADVANCE_SEARCH_BITS.SDT1, label: 'Low-power tags' },
  { key: 'searchDownvotedTags', bit: ADVANCE_SEARCH_BITS.SDT2, label: 'Downvoted tags' },
  { key: 'searchExpunged', bit: ADVANCE_SEARCH_BITS.SH, label: 'Include expunged' },
  { key: 'disableLanguageFilter', bit: ADVANCE_SEARCH_BITS.SFL, label: 'No language filter' },
  { key: 'disableUploaderFilter', bit: ADVANCE_SEARCH_BITS.SFU, label: 'No uploader filter' },
  { key: 'disableTagFilter', bit: ADVANCE_SEARCH_BITS.SFT, label: 'No tag filter' },
]

/** All advance switches (scope + higher) — preset bitmask packing order. */
export const ALL_ADVANCE_ITEMS = [...SCOPE_ITEMS, ...ADVANCED_ITEMS] as ReadonlyArray<{
  key: AdvanceSwitchKey
  bit: number
  label: string
}>

/** True when any filter field diverges from the no-filter default. */
export function isFilterActive(filters: SearchFilters): boolean {
  return filterChips(filters).length > 0
}

/**
 * Active-filter chip list for a filter state. Order: excluded categories
 * (one chip each, `CATEGORY_ORDER`), sort, page bounds, minimum rating,
 * scope switches. Chip ids are stable — see {@link FilterChip.id}.
 */
export function filterChips(filters: SearchFilters): FilterChip[] {
  const chips: FilterChip[] = []

  const mask = filters.category ?? 0
  for (const category of CATEGORY_ORDER) {
    const bit = CATEGORY_BIT_VALUES[category]
    if ((mask & bit) !== 0) {
      chips.push({ id: `category:${bit}`, label: `Excl. ${CATEGORY_LABELS[category]}` })
    }
  }

  if (filters.sort) {
    const option = SORT_OPTIONS.find((entry) => entry.value === filters.sort)
    chips.push({ id: 'sort', label: `Sort: ${option?.label ?? `#${filters.sort}`}` })
  }
  if (filters.pageMin && filters.pageMin > 0) {
    chips.push({ id: 'pageMin', label: `Pages ≥ ${filters.pageMin}` })
  }
  if (filters.pageMax && filters.pageMax > 0) {
    chips.push({ id: 'pageMax', label: `Pages ≤ ${filters.pageMax}` })
  }
  if (filters.minRating && filters.minRating > 0) {
    chips.push({ id: 'minRating', label: `Rating ≥ ${filters.minRating}★` })
  }
  for (const scope of SCOPE_ITEMS) {
    if (filters[scope.key]) {
      chips.push({ id: scope.key, label: `Scope: ${scope.label}` })
    }
  }
  for (const advanced of ADVANCED_ITEMS) {
    if (filters[advanced.key]) {
      chips.push({ id: advanced.key, label: `Adv: ${advanced.label}` })
    }
  }

  return chips
}

/**
 * Remove one chip's filter from the state (immutable). The field returns to
 * its default (absent) so neither the chip row nor the query string show it.
 * Unknown ids return the state unchanged.
 */
export function removeFilterChip(filters: SearchFilters, chipId: string): SearchFilters {
  const next: SearchFilters = { ...filters }

  if (chipId.startsWith('category:')) {
    const bit = Number(chipId.slice('category:'.length))
    if (!Number.isFinite(bit) || bit <= 0) return filters
    const mask = (next.category ?? 0) & ~bit
    next.category = mask === 0 ? undefined : mask
    return next
  }

  switch (chipId) {
    case 'sort':
      next.sort = undefined
      break
    case 'pageMin':
      next.pageMin = undefined
      break
    case 'pageMax':
      next.pageMax = undefined
      break
    case 'minRating':
      next.minRating = undefined
      break
    case 'searchName':
    case 'searchTags':
    case 'searchDesc':
    case 'searchTorrents':
    case 'searchTorrentsOnly':
    case 'searchLowPowerTags':
    case 'searchDownvotedTags':
    case 'searchExpunged':
    case 'disableLanguageFilter':
    case 'disableUploaderFilter':
    case 'disableTagFilter':
      next[chipId] = undefined
      break
    default:
      return filters
  }
  return next
}

/**
 * Filter state → `QuickSearchDto` request payload (sans server-assigned id).
 * Same schema as the openapi `QuickSearchDto`, so the result can be posted to
 * `POST /gallery/quick-search` unchanged.
 *
 * W3 R4-11: `sort` is part of the DTO now and round-trips through presets.
 */
export function filtersToQuickSearchPayload(
  filters: SearchFilters,
  context: { name: string; keyword: string; mode: number },
): Omit<QuickSearch, 'id'> {
  return {
    name: context.name,
    mode: context.mode,
    category: filters.category ?? 0,
    keyword: context.keyword,
    // W3 R4-10: presets carry the full 11-bit mask — the server validation
    // accepts 0..2047 and every bit is backend-supported now.
    advanceSearch: ALL_ADVANCE_ITEMS.reduce(
      (mask, item) => (filters[item.key] ? mask | item.bit : mask),
      0,
    ),
    minRating: filters.minRating ?? 0,
    pageFrom: filters.pageMin ?? 0,
    pageTo: filters.pageMax ?? 0,
    sort: filters.sort ?? 0,
  }
}

/**
 * `QuickSearchDto` → filter state (inverse of
 * {@link filtersToQuickSearchPayload}). The DTO `keyword`/`mode` belong to
 * the search box / keyword mode, not the filter state, so they are not
 * restored here. Scope booleans are emitted explicitly (false when the bit
 * is clear) so a round-trip is deterministic. `sort` 0 / absent (legacy
 * rows) reads back as "default order" (absent).
 */
export function quickSearchToFilters(preset: QuickSearch): SearchFilters {
  // All 11 advance switches are emitted explicitly (false when the bit is
  // clear) so a round-trip stays deterministic (W3 R4-10 full mask).
  const switches = ALL_ADVANCE_ITEMS.reduce(
    (acc, item) => {
      acc[item.key] = (preset.advanceSearch & item.bit) !== 0
      return acc
    },
    {} as Record<AdvanceSwitchKey, boolean>,
  )
  return {
    category: preset.category ? preset.category : undefined,
    minRating: preset.minRating ? preset.minRating : undefined,
    pageMin: preset.pageFrom ? preset.pageFrom : undefined,
    pageMax: preset.pageTo ? preset.pageTo : undefined,
    ...switches,
    // 0 / absent / out-of-range (legacy or hand-edited storage) all read as
    // "default order"; only the three contract orders pass through.
    sort: preset.sort === 1 || preset.sort === 2 || preset.sort === 3
      ? preset.sort
      : undefined,
  }
}

/** Exclusion bitmask → the categories INCLUDED (positive semantics). */
export function maskToIncluded(mask: number): GalleryCategory[] {
  return CATEGORY_ORDER.filter((category) => (mask & CATEGORY_BIT_VALUES[category]) === 0)
}

/** Included categories → exclusion bitmask (0 = all categories included). */
export function includedToMask(included: GalleryCategory[]): number {
  return CATEGORY_ORDER.reduce(
    (mask, category) => (included.includes(category) ? mask : mask | CATEGORY_BIT_VALUES[category]),
    0,
  )
}
