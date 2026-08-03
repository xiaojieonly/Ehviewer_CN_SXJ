// Gallery fixtures for the Gallery Site dummy server.
//
// Scenario matrix for the version-collapse feature:
//   - 1001 -> 1002 -> 1003 : three-level version chain (newest = 1003, both
//     1001 and 1002 must collapse under it in the downloads scene)
//   - 3001 -> 3002         : single-level update (the common two-version case)
//   - 2001 / 2002          : standalone galleries without any version relation
//
// `description` and `torrents` feed the advanced-search scope oracle
// (f_sdesc / f_storr in gallery.mjs). Their keywords are chosen so each
// scope has a marker that appears in that scope ONLY:
//   desc -> "commissioned" (2002), torr -> "alpha_final_archive" (1003),
//   tags -> "ponytail" (2001), name -> "revised" (3002 title).

export const EXH_BASE = 'https://gallery.test';

export const GALLERIES = [
  {
    gid: 1001,
    token: 'aaa1111111',
    title: 'Test Gallery Alpha',
    titleJpn: '',
    uploader: 'mock_user',
    category: 'Doujinshi',
    language: 'English',
    size: '1.2 MB',
    pages: 5,
    posted: '2024-01-01 12:00',
    favoriteCount: 0,
    rating: 4.5,
    ratingCount: 3,
    newVersions: [
      {
        name: 'Test Gallery Alpha (v2)',
        url: `${EXH_BASE}/g/1002/bbb2222222/`,
        time: '2024-02-01 08:00',
      },
    ],
    tags: [
      ['artist', ['mock_artist']],
      ['language', ['english']],
      ['parody', ['original']],
    ],
    description: 'First revision of the alpha test series.',
    torrents: [],
  },
  {
    gid: 1002,
    token: 'bbb2222222',
    title: 'Test Gallery Alpha (v2)',
    titleJpn: '',
    uploader: 'mock_user',
    category: 'Doujinshi',
    language: 'English',
    size: '1.4 MB',
    pages: 6,
    posted: '2024-02-01 08:00',
    favoriteCount: 0,
    rating: 4.7,
    ratingCount: 5,
    newVersions: [
      {
        name: 'Test Gallery Alpha (v3)',
        url: `${EXH_BASE}/g/1003/ccc3333333/`,
        time: '2024-03-01 10:30',
      },
    ],
    tags: [
      ['artist', ['mock_artist']],
      ['language', ['english']],
      ['parody', ['original']],
    ],
    description: 'Second pass with cleaned-up line art.',
    torrents: [],
  },
  {
    gid: 1003,
    token: 'ccc3333333',
    title: 'Test Gallery Alpha (v3)',
    titleJpn: '',
    uploader: 'mock_user',
    category: 'Doujinshi',
    language: 'English',
    size: '1.6 MB',
    pages: 7,
    posted: '2024-03-01 10:30',
    favoriteCount: 0,
    rating: 4.8,
    ratingCount: 6,
    newVersions: [],
    tags: [
      ['artist', ['mock_artist']],
      ['language', ['english']],
      ['parody', ['original']],
    ],
    description: 'Final pass of the alpha test series.',
    torrents: ['alpha_final_archive.torrent'],
  },
  {
    gid: 2001,
    token: 'ddd4444444',
    title: 'Standalone Gallery One',
    titleJpn: '',
    uploader: 'different_user',
    category: 'Manga',
    language: 'Japanese',
    size: '0.9 MB',
    pages: 4,
    posted: '2024-04-15 09:00',
    favoriteCount: 0,
    rating: 4.1,
    ratingCount: 8,
    newVersions: [],
    tags: [
      ['language', ['japanese']],
      ['female', ['ponytail']],
    ],
    description: 'A standalone Japanese sample.',
    torrents: [],
  },
  {
    gid: 2002,
    token: 'eee5555555',
    title: 'Standalone Gallery Two',
    titleJpn: '',
    uploader: 'different_user',
    category: 'Western',
    language: 'French',
    size: '2.1 MB',
    pages: 8,
    posted: '2024-05-20 14:00',
    favoriteCount: 0,
    rating: 3.9,
    ratingCount: 12,
    newVersions: [],
    tags: [
      ['language', ['french']],
      ['group', ['commission']],
      ['male', ['business_suit']],
    ],
    description: 'Commissioned French business suit set.',
    torrents: ['business_suit_set.torrent'],
  },
  {
    gid: 3001,
    token: 'fff6666666',
    title: 'Simple Update',
    titleJpn: '',
    uploader: 'mock_user',
    category: 'Doujinshi',
    language: 'English',
    size: '0.7 MB',
    pages: 3,
    posted: '2024-06-10 11:00',
    favoriteCount: 0,
    rating: 4.2,
    ratingCount: 4,
    newVersions: [
      {
        name: 'Simple Update (revised)',
        url: `${EXH_BASE}/g/3002/abc7777777/`,
        time: '2024-06-20 16:45',
      },
    ],
    tags: [
      ['artist', ['mock_artist']],
      ['language', ['english']],
    ],
    description: 'Simple update sample gallery.',
    torrents: [],
  },
  {
    gid: 3002,
    token: 'abc7777777',
    title: 'Simple Update (revised)',
    titleJpn: '',
    uploader: 'mock_user',
    category: 'Doujinshi',
    language: 'English',
    size: '0.9 MB',
    pages: 4,
    posted: '2024-06-20 16:45',
    favoriteCount: 0,
    rating: 4.4,
    ratingCount: 7,
    newVersions: [],
    tags: [
      ['artist', ['mock_artist']],
      ['language', ['english']],
    ],
    description: 'Follow-up to the simple update sample gallery.',
    torrents: [],
  },
];

export function findGallery(gid) {
  return GALLERIES.find((g) => g.gid === Number(gid));
}

// --------------------------------------------------------------- favorites
// Cloud-favorites fixtures for the Gallery Site `favorites.php` page (R4-8).
// FAVORITES maps a favorite-folder index (0..9) to the gids it contains. The
// shape is deliberately mixed: two folders with a single gallery, one folder
// with two galleries, and the rest empty — so the parser's 10-slot folder
// bar, the multi-row list, and the empty-folder ("No hits found") path are
// all exercised.

export const FAVORITE_FOLDERS = Array.from({ length: 10 }, (_, i) => `Favorites ${i}`);

export const FAVORITES = {
  0: [1003, 2002],
  1: [2001],
  2: [3002],
};

/** All favorited gids in deterministic folder order (0 -> 9). */
export function allFavoriteGids() {
  return FAVORITE_FOLDERS.flatMap((_, i) => FAVORITES[i] ?? []);
}

/** Count of galleries in a favorite folder. */
export function favoriteCount(i) {
  return (FAVORITES[i] ?? []).length;
}
