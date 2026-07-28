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

export default router;
