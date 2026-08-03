// Preferences get/put endpoints
import { Router } from 'express';

const router = Router();

// Defaults mirror PreferenceDto.kt (PreferenceResponse) — key set, order and
// default values must stay 1:1 with the DTO (Wave-1 P3 mock sync, task #8).
const DEFAULT_PREFERENCES = {
  general: {
    theme: 'light',
    themeAutoSwitch: false,
    launchPage: 'homepage',
    // grid|list — Wave-1 B-1: shared GalleryList default layout (grid)
    listMode: 'grid',
    showReadProgress: true,
    detailSize: 'long',
    thumbSize: 'middle',
    historyInfoSize: 100,
    showJpnTitle: false,
    showGalleryPages: false,
    showTagTranslations: true,
    showGalleryComment: true,
    showGalleryRating: true,
    showSiteEvents: true,
    showSiteLimits: true,
    // ---- Wave-1 B group (1b browsing consistency) ----
    showUploader: false,
    showPostedTime: false,
    // clamp -2..9 (WebUI clamps on input; server-side guard in the DTO)
    defaultFavoriteSlot: 0,
    // '|'-separated 10 favorite slot names; empty items fall back on the consumer side
    favoriteSlotNames: '',
    // recent-search retention count, 0 = disabled
    recentSearchMax: 10,
  },
  reader: {
    readingDirection: 'rtl',
    pageMode: 'dual',
    firstPageCover: true,
    pageScaling: 'fit',
    startPosition: 'top_right',
    autoPlayIntervalSec: 2,
    showProgress: true,
    showPageInterval: true,
    fullscreen: true,
    brightness: 0,
    // ---- Wave-1 A group (1c reader deepening) ----
    // black|gray|white
    backgroundColor: 'black',
    // threeZone|edgeOnly|disabled
    tapZoneScheme: 'threeZone',
    keyboardPaging: true,
    zoomStep: 1.5,
    maxZoom: 5,
    dualPageGap: 8,
    splitWidePages: false,
    preloadCount: 2,
    // slide|fade|none
    pageTransition: 'slide',
  },
  privacy: {
    enableAnalytics: true,
  },
};

// Per-user store — the real backend persists per-username via
// UserPreferenceRepository; the mock keeps an in-memory Map.
const store = new Map();

const DEFAULT_USERNAME = 'mock_user';

function cloneDefaults() {
  return {
    general: { ...DEFAULT_PREFERENCES.general },
    reader: { ...DEFAULT_PREFERENCES.reader },
    privacy: { ...DEFAULT_PREFERENCES.privacy },
  };
}

function currentFor(username) {
  let prefs = store.get(username);
  if (!prefs) {
    prefs = cloneDefaults();
    store.set(username, prefs);
  }
  return prefs;
}

// GET /api/v1/preferences
router.get('/', (req, res) => {
  res.json(currentFor(DEFAULT_USERNAME));
});

// PUT /api/v1/preferences — mirrors UserPreferenceService.update(): each
// non-null section in the body replaces that section wholesale; the merged
// result is stored and returned. Deep-merging is NOT done (same as backend).
router.put('/', (req, res) => {
  const body = req.body || {};
  const current = currentFor(DEFAULT_USERNAME);
  const merged = {
    general: body.general ?? current.general,
    reader: body.reader ?? current.reader,
    privacy: body.privacy ?? current.privacy,
  };
  store.set(DEFAULT_USERNAME, merged);
  res.json(merged);
});

export default router;
