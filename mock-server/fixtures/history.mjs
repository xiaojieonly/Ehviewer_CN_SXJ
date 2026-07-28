// History fixtures

import { galleries } from './galleries.mjs';

const CATEGORY_NAMES = {
  1: 'Doujinshi', 2: 'Manga', 4: 'Artist CG', 8: 'Western',
  16: 'Non-H', 32: 'Image Set', 64: 'Cosplay', 128: 'Asian Porn',
  256: 'Misc', 512: 'Game CG',
};

const now = Date.now();

export const historyItems = [
  galleries[0], galleries[5], galleries[12], galleries[18], galleries[3],
  galleries[8], galleries[14], galleries[20], galleries[1], galleries[23],
].map((g, i) => ({
  gid: g.gid,
  token: g.token,
  title: g.title,
  titleJpn: g.titleJpn,
  thumb: g.thumb,
  category: CATEGORY_NAMES[g.category] || 'Misc',
  rating: g.rating,
  mode: 0,
  time: now - i * 3600000 * (i + 1), // progressively older
}));

// Quick search presets
export const quickSearches = [
  {
    id: 1,
    name: 'Doujinshi EN',
    mode: 0,
    category: 1,
    keyword: 'language:english',
    advanceSearch: 0,
    minRating: 3,
    pageFrom: 0,
    pageTo: 0,
  },
  {
    id: 2,
    name: 'Full Color Manga',
    mode: 0,
    category: 2,
    keyword: 'full color',
    advanceSearch: 0,
    minRating: 4,
    pageFrom: 10,
    pageTo: 0,
  },
  {
    id: 3,
    name: 'Artist CG Sets',
    mode: 0,
    category: 4 | 32,
    keyword: null,
    advanceSearch: 0,
    minRating: 0,
    pageFrom: 0,
    pageTo: 0,
  },
];
