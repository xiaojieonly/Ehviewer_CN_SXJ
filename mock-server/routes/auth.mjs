// Auth endpoints
import { Router } from 'express';

const router = Router();

// In-memory stores
const users = new Map(); // username -> { password, createdAt }
const tokens = new Map(); // token -> { username, expiresAt }
const pairCodes = new Map(); // code -> { username, expiresAt }

const PAIR_CODE_TTL_MS = 10 * 60 * 1000;
const TOKEN_TTL_MS = 86400 * 1000;

// Mirrors routes/settings.mjs `settings.security.requireAuth` (that module keeps
// its store private). Default false to match the settings store.
let requireAuth = false;

export function isAuthRequired() {
  return requireAuth;
}

export function setAuthRequired(value) {
  requireAuth = !!value;
}

function generateToken(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

function extractToken(req) {
  const auth = req.headers.authorization;
  if (auth && auth.startsWith('Bearer ')) {
    return auth.slice(7);
  }
  return null;
}

export function validateToken(token) {
  if (!token) return false;
  const entry = tokens.get(token);
  if (!entry) return false;
  if (entry.expiresAt < Date.now()) {
    tokens.delete(token);
    return false;
  }
  return true;
}

function usernameForToken(token) {
  if (!validateToken(token)) return null;
  return tokens.get(token).username;
}

function issueToken(username, prefix) {
  const token = generateToken(prefix);
  tokens.set(token, { username, expiresAt: Date.now() + TOKEN_TTL_MS });
  return token;
}

function issuePairCode(username) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  const expiresAt = Date.now() + PAIR_CODE_TTL_MS;
  pairCodes.set(code, { username, expiresAt });
  return { code, expiresAt };
}

// POST /api/v1/auth/register
router.post('/register', (req, res) => {
  if (!isAuthRequired()) {
    return res.status(403).json({ success: false, message: 'Registration is disabled on this server', token: null, username: null });
  }
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ success: false, message: 'Username and password are required', token: null, username: null });
  }
  if (users.has(username)) {
    return res.status(400).json({ success: false, message: 'Username already exists', token: null, username: null });
  }
  users.set(username, { password, createdAt: Date.now() });
  const token = issueToken(username, 'mock-token');
  res.json({ success: true, message: 'Registration successful', token, username });
});

// POST /api/v1/auth/login
router.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ success: false, message: 'Username and password are required', token: null, username: null });
  }
  const user = users.get(username);
  if (!user || user.password !== password) {
    return res.status(400).json({ success: false, message: 'Invalid username or password', token: null, username: null });
  }
  const token = issueToken(username, 'mock-token');
  res.json({ success: true, message: 'Login successful', token, username });
});

// GET /api/v1/auth/status
router.get('/status', (req, res) => {
  const username = usernameForToken(extractToken(req));
  res.json({
    authenticated: username != null,
    username,
    authRequired: isAuthRequired(),
    ehSessionValid: false,
    ehSessionExpired: false,
  });
});

// POST /api/v1/auth/logout
router.post('/logout', (req, res) => {
  const token = extractToken(req);
  if (token) {
    tokens.delete(token);
  }
  res.json({ success: true, message: 'Logged out', token: null, username: null });
});

// POST /api/v1/auth/pair and /pair/start — generate a short-lived pairing code.
// Mirrors the real backend: requires an authenticated session when auth is required.
const generatePair = (req, res) => {
  const username = usernameForToken(extractToken(req));
  if (isAuthRequired() && username == null) {
    return res.status(401).json({ success: false, message: 'Authentication required' });
  }
  const { code, expiresAt } = issuePairCode(username ?? 'anonymous');
  res.json({ code, expiresAt });
};

router.post('/pair', generatePair);
router.post('/pair/start', generatePair);

// POST /api/v1/auth/pair/complete — exchange a single-use code for a device token
router.post('/pair/complete', (req, res) => {
  const { code, deviceId, deviceName } = req.body || {};
  if (!code || !deviceId || !deviceName) {
    return res.status(400).json({ success: false, message: 'Pairing code is invalid or expired', token: '', username: '' });
  }
  const entry = pairCodes.get(code);
  if (!entry || entry.expiresAt < Date.now()) {
    pairCodes.delete(code);
    return res.status(400).json({ success: false, message: 'Pairing code is invalid or expired', token: '', username: '' });
  }
  pairCodes.delete(code);
  const token = issueToken(entry.username, 'mock-device-token');
  res.json({ success: true, message: 'Pairing successful', token, username: entry.username });
});

export default router;
