// Auth endpoints
import { Router } from 'express';

const router = Router();

// POST /api/v1/auth/register
router.post('/register', (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.json({ success: false, message: 'Username and password are required', token: null, username: null });
  }
  res.json({
    success: true,
    message: 'Registration successful',
    token: `mock-token-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    username,
  });
});

// POST /api/v1/auth/login
router.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.json({ success: false, message: 'Invalid credentials', token: null, username: null });
  }
  res.json({
    success: true,
    message: 'Login successful',
    token: `mock-token-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    username,
  });
});

// GET /api/v1/auth/status
router.get('/status', (req, res) => {
  const auth = req.headers.authorization;
  if (auth && auth.startsWith('Bearer ')) {
    res.json({ authenticated: true, username: 'mock_user' });
  } else {
    res.json({ authenticated: false, username: null });
  }
});

// POST /api/v1/auth/logout
router.post('/logout', (req, res) => {
  res.json({ success: true, message: 'Logged out successfully', token: null, username: null });
});

// POST /api/v1/auth/pair — generate a short-lived pairing code
router.post('/pair', (req, res) => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  res.json({ code, expiresAt: Date.now() + 10 * 60 * 1000 });
});

// POST /api/v1/auth/pair/complete — exchange code for a device token
router.post('/pair/complete', (req, res) => {
  const { code, deviceId, deviceName, platform } = req.body || {};
  if (!code || !deviceId || !deviceName) {
    return res.json({ success: false, message: 'Pairing code is invalid or expired', token: '', username: null });
  }
  res.json({
    success: true,
    message: 'Pairing successful',
    token: `mock-device-token-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    username: 'default',
  });
});

export default router;
