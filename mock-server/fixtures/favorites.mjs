// Favorite fixtures

import { galleries } from './galleries.mjs';

const CATEGORY_NAMES = {
  1: 'Misc', 2: 'Doujinshi', 4: 'Manga', 8: 'Artist CG',
  16: 'Game CG', 32: 'Image Set', 64: 'Cosplay', 128: 'Asian Porn',
  256: 'Non-H', 512: 'Western',
};

function toFavoriteItem(g) {
  return {
    gid: g.gid,
    token: g.token,
    title: g.title,
    titleJpn: g.titleJpn,
    thumb: g.thumb,
    category: CATEGORY_NAMES[g.category] || 'Misc',
    rating: g.rating,
    uploader: g.uploader,
    posted: g.posted,
  };
}

// Slot 0 favorites
export const favoritesSlot0 = [
  galleries[0], galleries[2], galleries[6], galleries[15], galleries[18],
].map(toFavoriteItem);

// Slot 1 favorites
export const favoritesSlot1 = [
  galleries[4], galleries[7], galleries[10], galleries[16],
].map(toFavoriteItem);

// Slot 2 favorites
export const favoritesSlot2 = [
  galleries[1], galleries[3], galleries[12], galleries[19], galleries[22],
].map(toFavoriteItem);

// Local favorites (gallery/favorites endpoint)
export const localFavorites = [
  galleries[0], galleries[2], galleries[6], galleries[15], galleries[18],
  galleries[4], galleries[7], galleries[10],
];

export function getFavoritesBySlot(slot, page = 1) {
  const slots = { 0: favoritesSlot0, 1: favoritesSlot1, 2: favoritesSlot2 };
  const items = slots[slot] || favoritesSlot0;
  const pageSize = 25;
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const start = (page - 1) * pageSize;
  const paged = items.slice(start, start + pageSize);
  return { favorites: paged, totalPages, currentPage: page };
}
