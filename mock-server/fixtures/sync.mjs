// Sync entity fixtures

import { galleries } from './galleries.mjs';

const now = Date.now();
const deviceId = 'server-main';

function baseSyncFields(g) {
  return {
    gid: g.gid,
    token: g.token,
    title: g.title,
    titleJpn: g.titleJpn,
    thumb: g.thumb,
    category: g.category,
    posted: g.posted,
    uploader: g.uploader,
    rating: g.rating,
    rated: g.rated,
    simpleLanguage: g.simpleLanguage,
    simpleTags: g.simpleTags ? g.simpleTags.join(';') : null,
    thumbWidth: g.thumbWidth,
    thumbHeight: g.thumbHeight,
    spanSize: 1,
    spanIndex: 0,
    spanGroupIndex: 0,
    favoriteSlot: g.favoriteSlot,
    favoriteName: g.favoriteName,
    pages: g.pages,
  };
}

export const syncFavorites = [galleries[0], galleries[2], galleries[6]].map((g, i) => ({
  ...baseSyncFields(g),
  time: now - (i + 1) * 86400000,
  lastModified: now - (i + 1) * 86400000,
  deviceId,
  deleted: false,
}));

export const syncHistory = [galleries[0], galleries[5], galleries[12], galleries[18]].map((g, i) => ({
  ...baseSyncFields(g),
  mode: 0,
  time: now - (i + 1) * 3600000,
  lastModified: now - (i + 1) * 3600000,
  deviceId,
  deleted: false,
}));

export const syncDownloads = [galleries[0], galleries[6], galleries[9]].map((g, i) => ({
  ...baseSyncFields(g),
  state: [2, 3, 4][i],
  legacy: 0,
  time: now - (i + 1) * 7200000,
  label: ['Reading Queue', 'Artist Collection', null][i],
  archiveUri: null,
  total: g.pages,
  finished: [Math.floor(g.pages * 0.6), g.pages, 12][i],
  downloaded: 0,
  fileSize: [104857600, 209715200, -1][i],
  lastModified: now - (i + 1) * 7200000,
  deviceId,
  deleted: false,
}));

export const syncBookmarks = [galleries[0], galleries[4]].map((g, i) => ({
  ...baseSyncFields(g),
  page: [15, 42][i],
  time: now - (i + 1) * 1800000,
  lastModified: now - (i + 1) * 1800000,
  deviceId,
  deleted: false,
}));

export const syncFilters = [
  { mode: 0, text: 'bad title keyword', enabled: true, lastModified: now - 86400000, deviceId, deleted: false },
  { mode: 2, text: 'unwanted_tag', enabled: true, lastModified: now - 172800000, deviceId, deleted: false },
  { mode: 1, text: 'spam_uploader', enabled: false, lastModified: now - 259200000, deviceId, deleted: false },
];

export const syncQuickSearches = [
  {
    name: 'Doujinshi EN',
    mode: 0,
    category: 1,
    keyword: 'language:english',
    advanceSearch: 0,
    minRating: 3,
    pageFrom: 0,
    pageTo: 0,
    time: now - 604800000,
    lastModified: now - 604800000,
    deviceId,
    deleted: false,
  },
  {
    name: 'Full Color',
    mode: 0,
    category: 2,
    keyword: 'full color',
    advanceSearch: 0,
    minRating: 4,
    pageFrom: 10,
    pageTo: 0,
    time: now - 500000000,
    lastModified: now - 500000000,
    deviceId,
    deleted: false,
  },
];

export const syncDownloadLabels = [
  { label: 'Reading Queue', time: now - 700000000, lastModified: now - 700000000, deviceId, deleted: false },
  { label: 'Favorites Backup', time: now - 600000000, lastModified: now - 600000000, deviceId, deleted: false },
  { label: 'Artist Collection', time: now - 500000000, lastModified: now - 500000000, deviceId, deleted: false },
];

export const connectedDevices = [
  { deviceId: 'android-550e8400-e29b-41d4-a716-446655440000', deviceName: 'Pixel 8 Pro', platform: 'android', lastSeen: now - 3600000 },
  { deviceId: 'server-main', deviceName: 'Server-Main', platform: 'server', lastSeen: now - 60000 },
  { deviceId: 'web-browser-01', deviceName: 'Chrome Desktop', platform: 'web', lastSeen: now - 300000 },
];
