// Proxy endpoints
import { Router } from 'express';

const router = Router();

// Saved proxy settings. Mirrors the proxy block of /api/v1/settings; kept
// locally because routes/settings.mjs does not export its store. The real
// backend (WebProxyManager) reads from the shared config store, so values
// saved via the settings endpoint are used here when no body is given.
const saved = {
  enabled: false,
  type: 'http',
  host: '',
  port: 0,
  username: '',
  password: '',
};

// Mirrors ProxyController.mergeWithSaved(): a body field only overrides when
// present; absent fields fall back to the saved settings.
function mergeWithSaved(request) {
  const body = request || {};
  return {
    enabled: body.enabled ?? saved.enabled,
    type: body.type ?? saved.type,
    host: body.host ?? saved.host,
    port: body.port ?? saved.port,
    username: body.username ?? saved.username,
    password: body.password ?? saved.password,
  };
}

// Mirrors ProxyController.toProxy(): invalid configs fall back to a direct
// connection (NO_PROXY), same as the real backend.
function toProxy(s) {
  if (!s.enabled || !s.host || !s.host.trim() || !(s.port > 0 && s.port <= 65535)) return null;
  return {
    type: s.type === 'socks5' || s.type === 'socks' ? 'socks5' : 'http',
    host: s.host.trim(),
    port: s.port,
  };
}

// POST /api/v1/proxy/test — mirrors ProxyController.test(). The real backend
// performs an actual request to https://e-hentai.org/ through the proxy (8s
// connect/read timeouts) and returns success for any response < 500; the mock
// simulates the same decision tree deterministically.
router.post('/test', (req, res) => {
  const start = Date.now();
  const s = mergeWithSaved(req.body);
  const proxy = toProxy(s);
  if (!proxy) {
    // Direct connection (NO_PROXY) — e-hentai.org is reachable.
    return res.json({ success: true, latencyMs: Date.now() - start + 12, error: '' });
  }
  // Simulate a successful round-trip through the proxy.
  res.json({ success: true, latencyMs: Date.now() - start + 42, error: '' });
});

export default router;
