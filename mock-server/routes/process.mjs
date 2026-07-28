// Processing pipeline endpoints
import { Router } from 'express';

const router = Router();

// In-memory processing tasks
const tasks = new Map();
let taskCounter = 0;

// POST /api/v1/process/gallery/:id
router.post('/gallery/:id', (req, res) => {
  const galleryId = parseInt(req.params.id, 10);
  const body = req.body || {};
  const processingType = body.type || 'UPSCALE_2X';

  // Check for existing active task for this gallery
  for (const [, task] of tasks) {
    if (task.galleryId === galleryId && (task.state === 'PENDING' || task.state === 'PROCESSING')) {
      return res.status(409).json({ error: 'Processing already in progress for this gallery' });
    }
  }

  const taskId = `proc-${++taskCounter}-${Date.now()}`;
  const totalPages = 20 + Math.floor(Math.random() * 80);
  const task = {
    taskId,
    galleryId,
    totalPages,
    processedPages: 0,
    failedPages: 0,
    currentPage: -1,
    state: 'PENDING',
    processingType,
    processorId: 'waifu2x-mock',
    startedAt: null,
    completedAt: null,
    error: null,
  };
  tasks.set(taskId, task);

  // Simulate processing progression
  setTimeout(() => {
    task.state = 'PROCESSING';
    task.startedAt = new Date().toISOString();
    task.currentPage = 1;
  }, 500);

  const interval = setInterval(() => {
    if (task.processedPages >= task.totalPages) {
      clearInterval(interval);
      task.state = 'DONE';
      task.completedAt = new Date().toISOString();
      task.currentPage = -1;
      return;
    }
    task.processedPages++;
    task.currentPage = task.processedPages + 1;
  }, 300);

  res.json({
    taskId,
    galleryId,
    totalPages,
    state: 'PENDING',
  });
});

// GET /api/v1/process/status/:taskId
router.get('/status/:taskId', (req, res) => {
  const task = tasks.get(req.params.taskId);
  if (!task) {
    return res.status(404).json({ error: 'Task not found' });
  }
  res.json({
    taskId: task.taskId,
    galleryId: task.galleryId,
    state: task.state,
    totalPages: task.totalPages,
    processedPages: task.processedPages,
    failedPages: task.failedPages,
    currentPage: task.currentPage,
    startedAt: task.startedAt,
    completedAt: task.completedAt,
    error: task.error,
  });
});

export default router;
