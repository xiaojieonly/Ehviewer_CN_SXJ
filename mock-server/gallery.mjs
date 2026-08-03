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

import { EXH_BASE, GALLERIES, findGallery } from './gallery-fixtures.mjs';
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

function listHtml() {
  const rows = GALLERIES.map(
    (g) =>
      `<tr class="gtr"><td><div class="glthumb"><img src="${EXH_BASE}/t/${g.gid}/cover.jpg" style="max-width:100px;max-height:100px"/></div></td>` +
      `<td><div class="glname"><a href="${EXH_BASE}/g/${g.gid}/${g.token}/">${g.title}</a></div>` +
      `<div class="glhide">${g.pages} pages</div></td></tr>`,
  ).join('');
  return `<!DOCTYPE html><html><head><title>Mock Gallery Site</title></head><body>` +
    `<table class="itg">${rows}</table></body></html>`;
}

// -------------------------------------------------------------- routes

export default function galleryRoutes(app) {
  const router = express.Router();

  // Gallery home / search — shaped for GalleryListParser (table.itg/gtr/
  // glthumb/glname). Also handles ?f_search=... queries: same list page.
  router.get('/', (req, res) => {
    res.type('html').send(listHtml());
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

  // Search list page — best-effort shape for GalleryListParser.
  router.get('/', (req, res, next) => {
    if (req.query.f_search == null) {
      return next();
    }
    res.type('html').send(listHtml());
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
        filecount: g.pages,
      }));
    res.json({ gmetadata });
  });

  app.use(router);
}
