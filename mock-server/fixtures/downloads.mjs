// Download task fixtures - various states
// States: 0=pending, 1=downloading, 2=paused, 3=done, 4=failed

import { galleries } from './galleries.mjs';

const labels = [
  { id: 1, label: 'Reading Queue', time: 1720000000000 },
  { id: 2, label: 'Favorites Backup', time: 1720100000000 },
  { id: 3, label: 'Artist Collection', time: 1720200000000 },
];

export const downloads = [
  {
    id: 1001,
    gid: galleries[0].gid,
    token: galleries[0].token,
    title: galleries[0].title,
    titleJpn: galleries[0].titleJpn,
    thumb: galleries[0].thumb,
    category: galleries[0].category,
    state: 1, // downloading
    total: galleries[0].pages,
    done: Math.floor(galleries[0].pages * 0.6),
    label: 1,
    downloadDir: '/downloads/doujinshi/2801001',
  },
  {
    id: 1002,
    gid: galleries[2].gid,
    token: galleries[2].token,
    title: galleries[2].title,
    titleJpn: galleries[2].titleJpn,
    thumb: galleries[2].thumb,
    category: galleries[2].category,
    state: 0, // pending/waiting
    total: galleries[2].pages,
    done: 0,
    label: 1,
    downloadDir: '/downloads/doujinshi/2801003',
  },
  {
    id: 1003,
    gid: galleries[6].gid,
    token: galleries[6].token,
    title: galleries[6].title,
    titleJpn: galleries[6].titleJpn,
    thumb: galleries[6].thumb,
    category: galleries[6].category,
    state: 3, // completed
    total: galleries[6].pages,
    done: galleries[6].pages,
    label: 3,
    downloadDir: '/downloads/artist_cg/2801007',
  },
  {
    id: 1004,
    gid: galleries[9].gid,
    token: galleries[9].token,
    title: galleries[9].title,
    titleJpn: galleries[9].titleJpn,
    thumb: galleries[9].thumb,
    category: galleries[9].category,
    state: 4, // failed
    total: galleries[9].pages,
    done: 12,
    label: 2,
    downloadDir: '/downloads/western/2801010',
  },
  {
    id: 1005,
    gid: galleries[15].gid,
    token: galleries[15].token,
    title: galleries[15].title,
    titleJpn: galleries[15].titleJpn,
    thumb: galleries[15].thumb,
    category: galleries[15].category,
    state: 2, // paused
    total: galleries[15].pages,
    done: 150,
    label: 3,
    downloadDir: '/downloads/image_set/2801016',
  },
  {
    id: 1006,
    gid: galleries[4].gid,
    token: galleries[4].token,
    title: galleries[4].title,
    titleJpn: galleries[4].titleJpn,
    thumb: galleries[4].thumb,
    category: galleries[4].category,
    state: 3, // completed
    total: galleries[4].pages,
    done: galleries[4].pages,
    label: 0,
    downloadDir: '/downloads/manga/2801005',
  },
];

export function getDownloadsByLabel(label) {
  if (label === undefined || label === null) return { downloads, labels };
  const filtered = downloads.filter(d => d.label === label);
  return { downloads: filtered, labels };
}

export function getDownloadById(id) {
  return downloads.find(d => d.id === id) || null;
}

export { labels };
