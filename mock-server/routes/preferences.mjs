// Preferences get/put endpoints
import { Router } from 'express';

const router = Router();

// Defaults mirror PreferenceDto.kt (PreferenceResponse)
const DEFAULT_PREFERENCES = {
  general: {
    theme: 'light',
    themeAutoSwitch: false,
    launchPage: 'homepage',
    listMode: 'list',
    showReadProgress: true,
    detailSize: 'long',
    thumbSize: 'middle',
    historyInfoSize: 100,
    showJpnTitle: false,
    showGalleryPages: false,
    showTagTranslations: true,
    showGalleryComment: true,
    showGalleryRating: true,
    showEhEvents: true,
    showEhLimits: true,
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
