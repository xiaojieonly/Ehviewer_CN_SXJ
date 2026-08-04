// Gallery Site dummy server for small-scale functional testing.
//
// The Android app (debug build) rewrites requests for gallery.test /
// gallery.test / gallery.test to this server via OkHttp interceptor when
// BuildConfig.MOCK_EH_BASE_URL is set. The HTML below is deliberately shaped
// to satisfy the app's parsers (GalleryDetailParser / GalleryPageParser):
//
//   - detail page:  var gid/token script, #gnd new-version list, #gdd info
//                   table (Length: N pages), .gm cover, #gdt previews,
//                   #taglist, #cdiv comments
//   - image page:   <img src="..." style> plus showkey script
//   - images:       deterministic generated PNG/JPEG/GIF (see gallery-images.mjs)
//                   with the page number rendered on the picture itself, so
//                   download & reading can be verified by eye
//
// Fixtures (gallery-fixtures.mjs) include a three-level version chain
// (1001 -> 1002 -> 1003), a two-level chain (3001 -> 3002) and standalone
// galleries — the scenarios used to exercise the version-chain collapse
// feature in the downloads scene.

import express from 'express';

import {
  EXH_BASE,
  GALLERIES,
  findGallery,
  FAVORITE_FOLDERS,
  FAVORITES,
  allFavoriteGids,
  favoriteCount,
} from './gallery-fixtures.mjs';
import { makeImage } from './gallery-images.mjs';

// --------------------------------------------------------------- html

function detailHtml(g) {
  const newVersions = g.newVersions.map(
    (v) => `<a href="${v.url}">${v.name}</a> (Posted: <span>${v.time}</span>)`,
  );
  const gnd = newVersions.length
    ? `<div id="gnd">This gallery has new versions.<br>${newVersions.join('<br>')}</div>`
    : '<div id="gnd">This gallery has no new versions.</div>';

  const previews = [];
  for (let i = 1; i <= g.pages; i++) {
    previews.push(
      `<div class="gdtm"><div style="width:250px; height:350px; background:url(${EXH_BASE}/t/${g.gid}/${i}.jpg) 50% 50% no-repeat -0px"><a href="${EXH_BASE}/s/${g.token}/${g.gid}-${i}"><img alt="${i}"></a></div></div>`,
    );
  }

  // Pagination row — GalleryDetailParser.parsePreviewPages reads the page count
  // from .ptt (last page number before the "next" arrow).
  const pttCells = Array.from({ length: g.pages }, (_, i) => i + 1)
    .map((p) => `<td class="ptds"><a href="#" onclick="return false">${p}</a></td>`)
    .join('');
  const ptt = `<table class="ptt" style="margin:2px auto 0px"><tr><td class="ptdd">&lt;</td>${pttCells}<td onclick="return false">&gt;</td></tr></table>`;

  const tagRows = g.tags
    .map(
      ([ns, tags]) =>
        `<tr><td class="tc">${ns}:</td><td>${tags
          .map((t) => `<div class="gt"><a href="#">${t}</a></div>`)
          .join('')}</td></tr>`,
    )
    .join('');

  const comments = `<div id="cdiv"><div id="chd">Showing 1 comment</div>` +
    `<div class="c1"><div class="c2"><div class="c3">Posted on 01 Jan 2024, 10:00 by: &nbsp; <a href="#">mock_user</a></div>` +
    `<div class="c6" id="comment_1">Mock comment body</div><div class="c7"></div></div></div></div>`;

  return `<!DOCTYPE html><html><head><title>${g.title}</title></head><body>` +
    gnd +
    `<div class="gm"><div id="gd1"><div style="width:250px; height:350px; background:url(${EXH_BASE}/t/${g.gid}/cover.jpg)"></div></div>` +
    `<h1 id="gn">${g.title}</h1><h2 id="gj">${g.titleJpn}</h2>` +
    `<div id="gdc"><div class="cn">${g.category}</div></div>` +
    `<div id="gdn">${g.uploader}</div>` +
    `<div id="gdd"><table><tr><td>Posted:</td><td>${g.posted}</td></tr>` +
    `<tr><td>Language:</td><td>${g.language}</td></tr>` +
    `<tr><td>File Size:</td><td>${g.size}</td></tr>` +
    `<tr><td>Length:</td><td>${g.pages} pages</td></tr>` +
    `<tr><td>Favorited:</td><td>Never</td></tr></table></div>` +
    `<div id="rating_count">${g.ratingCount}</div>` +
    `<div id="rating_label">Average: ${g.rating.toFixed(2)}</div>` +
    `<div id="gdf">Add to Favorites</div>` +
    `<div id="gdt">${previews.join('')}</div>` +
    ptt +
    `</div>` +
    `<div id="taglist"><table>${tagRows}</table></div>` +
    comments +
    `<script>var gid = ${g.gid}; var token = "${g.token}"; var apiuid = -1; var apikey = "0000000000000000";</script>` +
    `</body></html>`;
}

function imagePageHtml(g, page) {
  const imageUrl = `${EXH_BASE}/image/${g.gid}/${page}`;
  return `<!DOCTYPE html><html><head><title>${g.title} Page ${page}</title></head><body>` +
    `<div id="i3"><img id="img" src="${imageUrl}.jpg" style="width:800px"></div>` +
    `<a href="${imageUrl}.jpgfullimg/view">Original</a>` +
    `<script>var showkey="ab12cd34";</script>` +
    `</body></html>`;
}

// One gallery row — shaped for GalleryListParser.parseGalleryInfo
// (table.itg > tr with .glthumb img / .glname a[href=/g/gid/token/] /
// .glhide). Shared by the home/search list and the favorites page.
function rowHtml(g) {
  return (
    `<tr class="gtr"><td><div class="glthumb"><img src="${EXH_BASE}/t/${g.gid}/cover.jpg" style="max-width:100px;max-height:100px"/></div></td>` +
    `<td><div class="glname"><a href="${EXH_BASE}/g/${g.gid}/${g.token}/">${g.title}</a></div>` +
    `<div class="glhide">${g.pages} pages</div></td></tr>`
  );
}

// Pager meta for the home/search list — the search-semantics oracle must be
// HONEST. Wire shape is the modern EH list page (cf. the GalleryListParserNew3
// capture in the app parser tests): a `.searchtext` "Found N results" row plus
// the `.searchnav` first/prev/next/last markers, and NO `.ptt` page table.
//
// Why exactly this shape: GalleryListParser maps it to pages=-1 and
// resultCount=N, and the backend (GalleryService.searchGallery) derives
// `total` from the parsed items whenever pages <= 0. A `.ptt` bar would
// instead trigger the backend's pages*25 estimate — with the mock's
// single-page corpus that produced the constant 1x25 meta observed in the V2
// regression (5 real cards, meta 25). `total` below is the FILTERED row
// count, so every f_search/f_cats/f_order combination reports truthfully.
// Page count is deliberately not asserted (the corpus is a single page and
// the modern shape carries no page cells), so nothing can contradict total.
// "results" stays plural for every count on purpose: the app matches
// PATTERN_RESULT_COUNT_PAGE = /Found .* results/ (singular would not parse).
function listPagerHtml(total) {
  const nav =
    `<div><span id="ufirst">&lt;&lt; First</span></div>` +
    `<div><span id="uprev">&lt; Prev</span></div>` +
    `<div id="ujumpbox" class="jumpbox"><a id="ujump" href="javascript:enable_jump_mode('u')">Jump/Seek</a></div>` +
    `<div><span id="unext">Next &gt;</span></div>` +
    `<div><span id="ulast">Last &gt;&gt;</span></div>`;
  return `<div class="searchtext"><p>Found ${total} results.</p></div>` +
    `<div class="searchnav"><div></div>${nav}</div>`;
}

function listHtml(galleries = GALLERIES) {
  const rows = galleries.map(rowHtml).join('');
  return `<!DOCTYPE html><html><head><title>Mock Gallery Site</title></head><body>` +
    listPagerHtml(galleries.length) +
    `<table class="itg">${rows}</table></body></html>`;
}

// Cloud favorites page — shaped for FavoritesParser (+ the GalleryListParser
// MODE_NORMAL it delegates to). DOM contract (FavoritesParser.java):
//   - .ido wrapper holding EXACTLY 11 .fp elements: the first 10 are the
//     favorite folders (child(0)=count, child(2)=name), the 11th is the
//     "all" row carrying class "fp fps";
//   - optional .searchnav <select> whose selected option -> favOrder;
//   - gallery list parsed by GalleryListParser: .itg rows (rowHtml) plus a
//     single-page .ptt bar; an EMPTY folder must instead contain the literal
//     "No hits found</p>" marker (no .ptt/.searchnav), which the parser maps
//     to pages=0 + empty list rather than throwing "No gallery".
function favoritesHtml(galleries) {
  const folderRows = FAVORITE_FOLDERS.map(
    (name, i) => `<div class="fp"><div>${favoriteCount(i)}</div><div></div><div>${name}</div></div>`,
  ).join('');
  const allCount = allFavoriteGids().length;
  const fpBar =
    `<div class="ido">${folderRows}` +
    `<div class="fp fps"><div>${allCount}</div><div></div><div>All</div></div></div>`;

  if (galleries.length === 0) {
    return `<!DOCTYPE html><html><head><title>Favorites</title></head><body>` +
      fpBar +
      `<div class="itg"><p>No hits found</p></div></body></html>`;
  }

  const rows = galleries.map(rowHtml).join('');
  const searchnav =
    `<div class="searchnav"><select name="sort">` +
    `<option value="f" selected="selected">Favorited Time</option>` +
    `<option value="p">Posted Time</option></select></div>`;
  const ptt =
    `<table class="ptt" style="margin:2px auto 0px"><tr>` +
    `<td class="ptdd">&lt;</td><td class="ptds">1</td><td class="ptdd">&gt;</td>` +
    `</tr></table>`;
  return `<!DOCTYPE html><html><head><title>Favorites</title></head><body>` +
    fpBar +
    searchnav +
    `<table class="itg">${rows}</table>` +
    ptt +
    `</body></html>`;
}

// ------------------------------------------- list query oracle (search v1.1)

// Category bit values mirror the Android core SiteConfig: the site's f_cats
// param is an EXCLUSION bitmask — a gallery is dropped when its category bit
// is set in f_cats.
const CATEGORY_BITS = {
  Misc: 0x1,
  Doujinshi: 0x2,
  Manga: 0x4,
  'Artist CG': 0x8,
  'Game CG': 0x10,
  'Image Set': 0x20,
  Cosplay: 0x40,
  'Asian Porn': 0x80,
  'Non-H': 0x100,
  Western: 0x200,
};

function flatTags(g) {
  return (g.tags ?? []).flatMap(([, tags]) => tags).map((t) => String(t).toLowerCase());
}

function matchesScopes(g, keyword, scopes) {
  const kw = keyword.toLowerCase();
  return scopes.some((scope) => {
    switch (scope) {
      case 'name':
        return (
          (g.title ?? '').toLowerCase().includes(kw) ||
          (g.titleJpn ?? '').toLowerCase().includes(kw)
        );
      case 'tags':
        return flatTags(g).some((t) => t.includes(kw));
      case 'desc':
        return (g.description ?? '').toLowerCase().includes(kw);
      case 'torr':
        return (g.torrents ?? []).some((t) => t.toLowerCase().includes(kw));
      default:
        return false;
    }
  });
}

// Sort oracle for f_order (contract: contracts/openapi.yaml `sort`):
// 0/absent = default fixture order, 1 = posted time desc, 2 = rating desc,
// 3 = title asc. Unknown values behave like 0.
function sortRows(rows, order) {
  const posted = (g) => new Date(String(g.posted).replace(' ', 'T')).getTime();
  if (order === 1) {
    rows.sort((a, b) => posted(b) - posted(a));
  } else if (order === 2) {
    rows.sort((a, b) => b.rating - a.rating);
  } else if (order === 3) {
    // locale-free code-unit compare so the oracle is deterministic
    rows.sort((a, b) => (a.title < b.title ? -1 : a.title > b.title ? 1 : 0));
  }
  return rows;
}

// Applies the Gallery Site list query params to the fixtures. Recognized
// (all optional; absent = no filtering / default order):
//   f_search                          keyword, matched per scope below
//   advsearch=1                       advanced-search carrier; required for
//                                     f_sr* / f_sp* on the real site too
//   f_sname/f_stags/f_sdesc/f_storr   keyword scope flags (union = OR).
//                                     Without advsearch — or with advsearch
//                                     but no scope flag — the site default
//                                     scope name+tags applies (core
//                                     DEFAULT_ADVANCE).
//   f_cats                            exclusion category bitmask
//   f_sr=on & f_srdd=N                keep rating >= N
//   f_sp=on & f_spf / f_spt           keep page count within [spf, spt];
//                                     either bound may be absent
//   f_sto=on                          keep only galleries with torrents
//                                     (W3 R4-10 higher-bit oracle)
//   f_sdt1 / f_sdt2 / f_sh /          W3 R4-10 higher bits — RECOGNIZED but
//   f_sfl / f_sfu / f_sft             no-ops here: fixtures carry no
//                                     low-power/downvoted-tag, expunged or
//                                     default language/uploader/tag filters
//                                     to model. The mock accepts them so
//                                     Tier-2 passthrough URLs never 400.
//   f_order                           sort oracle (see sortRows)
//   page                              pagination is not modeled — the mock
//                                     list is a single page
export function applyListQuery(galleries, q) {
  let rows = [...galleries];

  if (q.f_cats != null && q.f_cats !== '') {
    const excluded = Number(q.f_cats) || 0;
    rows = rows.filter((g) => {
      const bit = CATEGORY_BITS[g.category] ?? 0;
      return (excluded & bit) === 0;
    });
  }

  const keyword = q.f_search != null ? String(q.f_search).trim() : '';
  if (keyword) {
    let scopes;
    if (q.advsearch === '1') {
      scopes = [];
      if (q.f_sname === 'on') scopes.push('name');
      if (q.f_stags === 'on') scopes.push('tags');
      if (q.f_sdesc === 'on') scopes.push('desc');
      if (q.f_storr === 'on') scopes.push('torr');
      if (scopes.length === 0) scopes = ['name', 'tags'];
    } else {
      scopes = ['name', 'tags'];
    }
    rows = rows.filter((g) => matchesScopes(g, keyword, scopes));
  }

  if (q.f_sr === 'on' && q.f_srdd != null && q.f_srdd !== '') {
    const min = Number(q.f_srdd);
    if (!Number.isNaN(min)) {
      rows = rows.filter((g) => g.rating >= min);
    }
  }

  if (q.f_sp === 'on') {
    const from = q.f_spf != null && q.f_spf !== '' ? Number(q.f_spf) : null;
    const to = q.f_spt != null && q.f_spt !== '' ? Number(q.f_spt) : null;
    rows = rows.filter(
      (g) =>
        (from == null || Number.isNaN(from) || g.pages >= from) &&
        (to == null || Number.isNaN(to) || g.pages <= to),
    );
  }

  // W3 R4-10: f_sto — only galleries with torrents. The other higher bits
  // (f_sdt1/f_sdt2/f_sh/f_sfl/f_sfu/f_sft) are accepted no-ops (see header).
  if (q.f_sto === 'on') {
    rows = rows.filter((g) => Array.isArray(g.torrents) && g.torrents.length > 0);
  }

  return sortRows(rows, Number(q.f_order) || 0);
}

// -------------------------------------------------------------- routes

export default function galleryRoutes(app) {
  const router = express.Router();

  // Gallery home / search — shaped for GalleryListParser (table.itg/gtr/
  // glthumb/glname). Applies the site list query params (f_search scopes,
  // f_cats, f_order, f_spf/f_spt, f_sr/f_srdd, advsearch) via applyListQuery;
  // with no params it returns the full fixture list in default order. The
  // pager meta (listPagerHtml) reports the FILTERED row count — see its
  // comment for why the shape keeps the backend `total` honest.
  router.get('/', (req, res) => {
    res.type('html').send(listHtml(applyListQuery(GALLERIES, req.query)));
  });

  // Cloud favorites (R4-8) — shaped for FavoritesParser. `favcat` selects a
  // folder ("0".."9"); anything else (absent / "all") returns every favorited
  // gallery. The 10-slot folder bar and favOrder <select> are always present,
  // so catArray / countArray / favOrder parse regardless of the folder. An
  // empty folder returns the "No hits found" shape (pages=0, empty list).
  router.get('/favorites.php', (req, res) => {
    const favcat = req.query.favcat;
    const gids =
      favcat != null && /^\d$/.test(String(favcat))
        ? FAVORITES[Number(favcat)] ?? []
        : allFavoriteGids();
    const galleries = gids.map(findGallery).filter(Boolean);
    res.type('html').send(favoritesHtml(galleries));
  });

  // Gallery detail page — shaped for GalleryDetailParser.
  router.get('/g/:gid/:token', (req, res) => {
    const g = findGallery(req.params.gid);
    if (!g) {
      return res.status(404).send('<div class="d"><p>Gallery not found</p></div>');
    }
    res.type('html').send(detailHtml(g));
  });

  // Image page — shaped for GalleryPageParser.
  router.get('/s/:pToken/:gidPage', (req, res) => {
    const match = /^(\d+)-(\d+)$/.exec(req.params.gidPage);
    if (!match) {
      return res.status(404).send('not found');
    }
    const g = findGallery(match[1]);
    if (!g) {
      return res.status(404).send('not found');
    }
    res.type('html').send(imagePageHtml(g, Number(match[2])));
  });

  // Raw image bytes. The extension selects the format — the app saves
  // download files using the URL extension, so format must match it.
  //   /image/{gid}/{page}      -> png (default, no extension)
  //   /image/{gid}/{page}.jpg  -> jpeg
  //   /image/{gid}/{page}.png  -> png
  //   /image/{gid}/{page}.gif  -> gif
  router.get('/image/:gid/:pageAndExt', (req, res) => {
    const g = findGallery(req.params.gid);
    if (!g) {
      return res.status(404).send('not found');
    }
    const match = /^(\d+)(?:\.(png|jpg|jpeg|gif))?$/.exec(req.params.pageAndExt);
    if (!match) {
      return res.status(404).send('not found');
    }
    const page = Number(match[1]);
    const format = match[2] ?? 'png';
    const mime = format === 'jpg' || format === 'jpeg' ? 'image/jpeg' : `image/${format}`;
    res.type(mime).send(makeImage(g.gid, page, format, g.pages));
  });

  // Thumbnails (cover + per-page thumbs).
  router.get('/t/:gid/:name', (req, res) => {
    const g = findGallery(req.params.gid);
    if (!g) {
      return res.status(404).send('not found');
    }
    res.type('image/png').send(makeImage(g.gid, 1, 'png', g.pages));
  });

  // Favorites popup + API stub — not needed for the core test loop.
  router.get('/gallerypopups.php', (req, res) => res.send(''));
  // Gallery Site API (api.php): two methods used by the app.
  //  - method=gdata: fill list items with metadata (GalleryApiParser)
  //  - method=showpage: reader page image URL (GalleryPageApiParser: i3/i6)
  router.post('/api.php', (req, res) => {
    const body = req.body ?? {};
    if (body.method === 'showpage') {
      const gid = Number(body.gid);
      const page = Number(body.page) || 1;
      const image = `${EXH_BASE}/image/${gid}/${page}.jpg`;
      res.json({
        i3: `<img src="${image}" style="width:800px">`,
        i6: `<a href="#" onclick="prompt('Copy the URL below.', '${image}fullimg/view')">Original</a>`,
        i7: null,
      });
      return;
    }
    // default: gdata
    const gidlist = Array.isArray(body.gidlist) ? body.gidlist : [];
    const gmetadata = gidlist
      .map(([gid]) => findGallery(String(gid)))
      .filter(Boolean)
      .map((g) => ({
        gid: g.gid,
        token: g.token,
        title: g.title,
        title_jpn: g.titleJpn ?? '',
        category: g.category,
        thumb: `${EXH_BASE}/t/${g.gid}/cover.jpg`,
        uploader: g.uploader,
        posted: String(Math.floor(new Date(g.posted).getTime() / 1000) || 0),
        rating: String(g.rating),
        tags: (g.tags ?? []).flatMap(([ns, tags]) => tags.map((t) => `${ns}:${t}`)),
        // EH api.php wire format: numeric fields travel as strings (the core
        // GalleryApiParser reads them via getString).
        filecount: String(g.pages),
      }));
    res.json({ gmetadata });
  });

  app.use(router);
}
