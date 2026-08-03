import { describe, it, expect } from 'vitest'
import type { QuickSearch } from '@/types'
import type { SearchFilters } from '@/api/gallery'
import { ADVANCE_SEARCH_BITS, CATEGORY_BIT_VALUES, CATEGORY_ORDER } from '@/types/components'
import {
  filterChips,
  filtersToQuickSearchPayload,
  includedToMask,
  isFilterActive,
  maskToIncluded,
  quickSearchToFilters,
  removeFilterChip,
} from '../searchFilters'

const FULL_FILTERS: SearchFilters = {
  category: CATEGORY_BIT_VALUES.manga | CATEGORY_BIT_VALUES.misc,
  sort: 2,
  pageMin: 5,
  pageMax: 50,
  minRating: 4,
  searchName: true,
  searchTags: true,
  searchDesc: true,
  searchTorrents: true,
}

describe('filterChips — filter state → chip row', () => {
  it('produces no chips for an empty / default filter state', () => {
    expect(filterChips({})).toEqual([])
    expect(
      filterChips({ sort: 0, pageMin: 0, pageMax: 0, minRating: 0 }),
    ).toEqual([])
    expect(isFilterActive({})).toBe(false)
  })

  it('emits one chip per excluded category, labelled and in CATEGORY_ORDER', () => {
    const chips = filterChips({
      category: CATEGORY_BIT_VALUES.misc | CATEGORY_BIT_VALUES.doujinshi,
    })
    // CATEGORY_ORDER starts doujinshi, manga, …, misc — chips keep that order.
    expect(chips).toEqual([
      { id: `category:${CATEGORY_BIT_VALUES.doujinshi}`, label: 'Excl. Doujinshi' },
      { id: `category:${CATEGORY_BIT_VALUES.misc}`, label: 'Excl. Misc' },
    ])
  })

  it('renders sort / page bounds / minimum rating / scope chips', () => {
    const chips = filterChips(FULL_FILTERS)
    const byId = new Map(chips.map((chip) => [chip.id, chip.label]))
    expect(byId.get('sort')).toBe('Sort: Rating — highest first')
    expect(byId.get('pageMin')).toBe('Pages ≥ 5')
    expect(byId.get('pageMax')).toBe('Pages ≤ 50')
    expect(byId.get('minRating')).toBe('Rating ≥ 4★')
    expect(byId.get('searchName')).toBe('Scope: Gallery name')
    expect(byId.get('searchTags')).toBe('Scope: Gallery tags')
    expect(byId.get('searchDesc')).toBe('Scope: Description')
    expect(byId.get('searchTorrents')).toBe('Scope: Torrent filenames')
    expect(chips).toHaveLength(2 + 8) // 2 categories + 8 scalar/scope chips
    expect(isFilterActive(FULL_FILTERS)).toBe(true)
  })
})

describe('removeFilterChip — chip × restores the default per case', () => {
  it('clears a single excluded category bit, keeping the others', () => {
    const next = removeFilterChip(FULL_FILTERS, `category:${CATEGORY_BIT_VALUES.manga}`)
    expect(next.category).toBe(CATEGORY_BIT_VALUES.misc)
    // Other fields untouched.
    expect(next.sort).toBe(2)
    expect(next.minRating).toBe(4)
  })

  it('drops the category field entirely when the last bit is cleared', () => {
    const next = removeFilterChip(
      { category: CATEGORY_BIT_VALUES.western },
      `category:${CATEGORY_BIT_VALUES.western}`,
    )
    expect(next.category).toBeUndefined()
    expect(filterChips(next)).toEqual([])
  })

  it.each([
    ['sort', 'sort'],
    ['pageMin', 'pageMin'],
    ['pageMax', 'pageMax'],
    ['minRating', 'minRating'],
    ['searchName', 'searchName'],
    ['searchTags', 'searchTags'],
    ['searchDesc', 'searchDesc'],
    ['searchTorrents', 'searchTorrents'],
  ] as const)('clears the %s chip', (chipId, field) => {
    const next = removeFilterChip(FULL_FILTERS, chipId)
    expect(next[field]).toBeUndefined()
    expect(filterChips(next).some((chip) => chip.id === chipId)).toBe(false)
  })

  it('is immutable and ignores unknown chip ids', () => {
    // Unknown id → the same state object, unchanged.
    expect(removeFilterChip(FULL_FILTERS, 'bogus')).toEqual(FULL_FILTERS)
    removeFilterChip(FULL_FILTERS, 'sort')
    expect(FULL_FILTERS.sort).toBe(2) // original untouched by a valid removal
  })
})

describe('quick-search schema consistency (QuickSearchDto round-trip)', () => {
  it('builds exactly the QuickSearchDto fields (sans id) from the filter state', () => {
    const payload = filtersToQuickSearchPayload(FULL_FILTERS, {
      name: 'Strict search',
      keyword: 'naruto',
      mode: 0,
    })
    expect(Object.keys(payload).sort()).toEqual(
      ['advanceSearch', 'category', 'keyword', 'minRating', 'mode', 'name', 'pageFrom', 'pageTo', 'sort'].sort(),
    )
    expect(payload).toEqual({
      name: 'Strict search',
      mode: 0,
      category: CATEGORY_BIT_VALUES.manga | CATEGORY_BIT_VALUES.misc,
      keyword: 'naruto',
      advanceSearch:
        ADVANCE_SEARCH_BITS.SNAME |
        ADVANCE_SEARCH_BITS.STAGS |
        ADVANCE_SEARCH_BITS.SDESC |
        ADVANCE_SEARCH_BITS.STORR,
      minRating: 4,
      pageFrom: 5,
      pageTo: 50,
      sort: 2, // W3 R4-11: sort round-trips through presets now
    })
  })

  it('defaults unset filter fields to the DTO zero values', () => {
    const payload = filtersToQuickSearchPayload({}, { name: 'Plain', keyword: '', mode: 2 })
    expect(payload).toEqual({
      name: 'Plain',
      mode: 2,
      category: 0,
      keyword: '',
      advanceSearch: 0,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
      sort: 0,
    })
  })

  it('round-trips filter state through the DTO (sort included, W3 R4-11)', () => {
    const payload = filtersToQuickSearchPayload(FULL_FILTERS, {
      name: 'x',
      keyword: 'k',
      mode: 0,
    })
    const preset: QuickSearch = { id: 1, ...payload }
    expect(quickSearchToFilters(preset)).toEqual({
      category: CATEGORY_BIT_VALUES.manga | CATEGORY_BIT_VALUES.misc,
      minRating: 4,
      pageMin: 5,
      pageMax: 50,
      searchName: true,
      searchTags: true,
      searchDesc: true,
      searchTorrents: true,
      sort: 2,
    })
  })

  it('maps a zeroed preset back to an inactive filter state', () => {
    const preset: QuickSearch = {
      id: 9,
      name: 'empty',
      mode: 0,
      category: 0,
      keyword: '',
      advanceSearch: 0,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
    }
    const filters = quickSearchToFilters(preset)
    expect(filters.category).toBeUndefined()
    expect(filters.minRating).toBeUndefined()
    expect(filters.pageMin).toBeUndefined()
    expect(filters.pageMax).toBeUndefined()
    expect(filters.searchName).toBe(false)
    expect(filters.searchTags).toBe(false)
    expect(filters.searchDesc).toBe(false)
    expect(filters.searchTorrents).toBe(false)
    expect(filters.sort).toBeUndefined() // absent sort (legacy row) = default order
    expect(isFilterActive(filters)).toBe(false)
  })

  it('keeps partial scope bits distinct (only the set bit survives)', () => {
    const preset: QuickSearch = {
      id: 2,
      name: 'tags only',
      mode: 0,
      category: 0,
      keyword: '',
      advanceSearch: ADVANCE_SEARCH_BITS.STAGS,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
    }
    const filters = quickSearchToFilters(preset)
    expect(filters.searchTags).toBe(true)
    expect(filters.searchName).toBe(false)
    expect(filters.searchDesc).toBe(false)
    expect(filters.searchTorrents).toBe(false)
  })
})

describe('category bitmask ↔ included-list helpers', () => {
  it('treats mask 0 as "all categories included" and 0x3ff as none', () => {
    expect(maskToIncluded(0)).toEqual([...CATEGORY_ORDER])
    expect(maskToIncluded(0x3ff)).toEqual([])
  })

  it('round-trips included selections through the exclusion mask', () => {
    const included = ['manga', 'western'] as const
    const mask = includedToMask([...included])
    expect(mask).toBe(
      CATEGORY_ORDER.reduce(
        (m, c) => ((included as readonly string[]).includes(c) ? m : m | CATEGORY_BIT_VALUES[c]),
        0,
      ),
    )
    expect(maskToIncluded(mask)).toEqual([...included])
  })
})
