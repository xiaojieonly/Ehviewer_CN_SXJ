/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.glview.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.hippo.lib.glview.anim.CanvasAnimation;
import com.hippo.lib.glview.glrenderer.BasicTexture;
import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.glview.glrenderer.GLES11Canvas;
import com.hippo.lib.glview.glrenderer.GLES20Canvas;
import com.hippo.lib.glview.glrenderer.UploadedTexture;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.util.ReadingRefreshRate;
import com.hippo.lib.glview.util.ApiHelper;
import com.hippo.lib.glview.util.GalleryUtils;
import com.hippo.lib.glview.util.MotionEventHelper;
import com.hippo.lib.yorozuya.AssertUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

// TODO Call attachToRoot and detachFromRoot in render thread
// The root component of all <code>GLView</code>s. The rendering is done in GL
// thread while the event handling is done in the main thread.  To synchronize
// the two threads, the entry points of this package need to synchronize on the
// <code>GLRootView</code> instance unless it can be proved that the rendering
// thread won't access the same thing as the method. The entry points include:
// (1) The public methods of HeadUpDisplay
// (2) The public methods of CameraHeadUpDisplay
// (3) The overridden methods in GLRootView.
public class GLRootView extends GLSurfaceView
        implements GLRoot {
    private static final String TAG = "GLRootView";

    private static final boolean DEBUG_FPS = false;
    private int mFrameCount = 0;
    private long mFrameCountingStart = 0;

    private static final boolean DEBUG_INVALIDATE = false;
    private int mInvalidateColor = 0;

    private static final boolean DEBUG_DRAWING_STAT = false;

    private static final int FLAG_INITIALIZED = 1;
    private static final int FLAG_NEED_LAYOUT = 2;

    private GL11 mGL;
    private GLCanvas mCanvas;
    private GLView mContentView;

    private OrientationSource mOrientationSource;
    // mCompensation is the difference between the UI orientation on GLCanvas
    // and the framework orientation. See OrientationManager for details.
    private int mCompensation;
    // mCompensationMatrix maps the coordinates of touch events. It is kept sync
    // with mCompensation.
    private final Matrix mCompensationMatrix = new Matrix();
    private int mDisplayRotation;

    private int mFlags = FLAG_NEED_LAYOUT;
    private volatile boolean mRenderRequested = false;

    private final ArrayList<CanvasAnimation> mAnimations =
            new ArrayList<>();

    private final ArrayDeque<OnGLIdleListener> mIdleListeners =
            new ArrayDeque<>();

    private final IdleRunner mIdleRunner = new IdleRunner();

    private final ReentrantLock mRenderLock = new ReentrantLock();
    private final Condition mFreezeCondition =
            mRenderLock.newCondition();
    private boolean mFreeze;

    private boolean mInDownState = false;
    private int mEGLContextClientVersion;

    public GLRootView(Context context) {
        this(context, null);
    }

    @SuppressWarnings("deprecation")
    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFlags |= FLAG_INITIALIZED;
        setBackgroundDrawable(null);

        mEGLContextClientVersion = 2;
        setEGLContextClientVersion(mEGLContextClientVersion);
        ConfigChooser configChooser = new ConfigChooser();
        setEGLConfigChooser(configChooser);
        setEGLContextFactory(configChooser);
        setRenderer(new GLRootRenderer());
        getHolder().setFormat(PixelFormat.RGBA_8888);

        // Uncomment this to enable gl error check.
        // setDebugFlags(DEBUG_CHECK_GL_ERROR);
    }

    @Override
    public void registerLaunchedAnimation(CanvasAnimation animation) {
        // Register the newly launched animation so that we can set the start
        // time more precisely. (Usually, it takes much longer for first
        // rendering, so we set the animation start time as the time we
        // complete rendering)
        mAnimations.add(animation);
    }

    @Override
    public void addOnGLIdleListener(OnGLIdleListener listener) {
        synchronized (mIdleListeners) {
            mIdleListeners.addLast(listener);
            if (mCanvas != null) {
                // Wait for onSurfaceCreated
                mIdleRunner.enable();
            }
        }
    }

    @Override
    public void setContentPane(GLView content) {
        if (mContentView == content) return;
        if (mContentView != null) {
            if (mInDownState) {
                long now = SystemClock.uptimeMillis();
                MotionEvent cancelEvent = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                mContentView.dispatchTouchEvent(cancelEvent);
                cancelEvent.recycle();
                mInDownState = false;
            }
            mContentView.detachFromRoot();
            BasicTexture.yieldAllTextures();
        }
        mContentView = content;
        if (content != null) {
            content.attachToRoot(this);
            requestLayoutContentPane();
        }
    }

    @Override
    public void requestRenderForced() {
        superRequestRender();
    }

    @Override
    public void requestRender() {
        if (DEBUG_INVALIDATE) {
            StackTraceElement e = Thread.currentThread().getStackTrace()[4];
            String caller = e.getFileName() + ":" + e.getLineNumber() + " ";
            Log.d(TAG, "invalidate: " + caller);
        }
        if (mRenderRequested) return;
        mRenderRequested = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(mRequestRenderOnAnimationFrame);
        } else {
            super.requestRender();
        }
    }

    private final Runnable mRequestRenderOnAnimationFrame = new Runnable() {
        @Override
        public void run() {
            superRequestRender();
        }
    };

    private void superRequestRender() {
        super.requestRender();
    }

    @Override
    public void requestLayoutContentPane() {
        mRenderLock.lock();
        try {
            if (mContentView == null || (mFlags & FLAG_NEED_LAYOUT) != 0) return;

            // "View" system will invoke onLayout() for initialization(bug ?), we
            // have to ignore it since the GLThread is not ready yet.
            if ((mFlags & FLAG_INITIALIZED) == 0) return;

            mFlags |= FLAG_NEED_LAYOUT;
            requestRender();
        } finally {
            mRenderLock.unlock();
        }
    }

    private void layoutContentPane() {
        mFlags &= ~FLAG_NEED_LAYOUT;

        int w = getWidth();
        int h = getHeight();
        int displayRotation;
        int compensation;

        // Get the new orientation values
        if (mOrientationSource != null) {
            displayRotation = mOrientationSource.getDisplayRotation();
            compensation = mOrientationSource.getCompensation();
        } else {
            displayRotation = 0;
            compensation = 0;
        }

        if (mCompensation != compensation) {
            mCompensation = compensation;
            if (mCompensation % 180 != 0) {
                mCompensationMatrix.setRotate(mCompensation);
                // move center to origin before rotation
                mCompensationMatrix.preTranslate(-w / 2, -h / 2);
                // align with the new origin after rotation
                mCompensationMatrix.postTranslate(h / 2, w / 2);
            } else {
                mCompensationMatrix.setRotate(mCompensation, w / 2, h / 2);
            }
        }
        mDisplayRotation = displayRotation;

        // Do the actual layout.
        if (mCompensation % 180 != 0) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        Log.i(TAG, "layout content pane " + w + "x" + h + " (compensation " + mCompensation + ")");
        if (mContentView != null && w != 0 && h != 0) {
            mContentView.measure(GLView.MeasureSpec.makeMeasureSpec(w, GLView.MeasureSpec.EXACTLY),
                    GLView.MeasureSpec.makeMeasureSpec(h, GLView.MeasureSpec.EXACTLY));
            mContentView.layout(0, 0, w, h);
        }
        // Uncomment this to dump the view hierarchy.
        //mContentView.dumpTree("");
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (changed) requestLayoutContentPane();
    }

    private void outputFps() {
        long now = System.nanoTime();
        if (mFrameCountingStart == 0) {
            mFrameCountingStart = now;
        } else if ((now - mFrameCountingStart) > 1000000000) {
            Log.d(TAG, "fps: " + (double) mFrameCount
                    * 1000000000 / (now - mFrameCountingStart));
            mFrameCountingStart = now;
            mFrameCount = 0;
        }
        ++mFrameCount;
    }

    private void onDrawFrameLocked(GL10 gl) {
        if (DEBUG_FPS) outputFps();
        // release the unbound textures and deleted buffers.
        mCanvas.deleteRecycledResources();

        // reset texture upload limit
        UploadedTexture.resetUploadLimit();

        mRenderRequested = false;

        if ((mOrientationSource != null
                && mDisplayRotation != mOrientationSource.getDisplayRotation())
                || (mFlags & FLAG_NEED_LAYOUT) != 0) {
            layoutContentPane();
        }

        mCanvas.save(GLCanvas.SAVE_FLAG_ALL);
        rotateCanvas(-mCompensation);
        if (mContentView != null) {
           mContentView.render(mCanvas);
        } else {
            // Make sure we always draw something to prevent displaying garbage
            mCanvas.clearBuffer();
        }
        mCanvas.restore();

        if (!mAnimations.isEmpty()) {
            long now = AnimationTime.get();
            for (int i = 0, n = mAnimations.size(); i < n; i++) {
                mAnimations.get(i).setStartTime(now);
            }
            mAnimations.clear();
        }

        if (UploadedTexture.uploadLimitReached()) {
            requestRender();
        }

        synchronized (mIdleListeners) {
            if (!mIdleListeners.isEmpty()) mIdleRunner.enable();
        }

        if (DEBUG_INVALIDATE) {
            mCanvas.fillRect(10, 10, 5, 5, mInvalidateColor);
            mInvalidateColor = ~mInvalidateColor;
        }

        if (DEBUG_DRAWING_STAT) {
            mCanvas.dumpStatisticsAndClear();
        }
    }

    private void rotateCanvas(int degrees) {
        if (degrees == 0) return;
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        mCanvas.translate(cx, cy);
        mCanvas.rotate(degrees, 0, 0, 1);
        if (degrees % 180 != 0) {
            mCanvas.translate(-cy, -cx);
        } else {
            mCanvas.translate(-cx, -cy);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_UP) {
            mInDownState = false;
        } else if (!mInDownState && action != MotionEvent.ACTION_DOWN) {
            return false;
        }

        if (mCompensation != 0) {
            event = MotionEventHelper.transformEvent(event, mCompensationMatrix);
        }

        mRenderLock.lock();
        try {
            // If this has been detached from root, we don't need to handle event
            boolean handled = mContentView != null
                    && mContentView.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_DOWN && handled) {
                mInDownState = true;
            }
            return handled;
        } finally {
            mRenderLock.unlock();
        }
    }

    @Override
    public void lockRenderThread() {
        mRenderLock.lock();
    }

    @Override
    public void unlockRenderThread() {
        mRenderLock.unlock();
    }

    @Override
    public void onPause() {
        unfreeze();
        super.onPause();
    }

    @Override
    public void setOrientationSource(OrientationSource source) {
        mOrientationSource = source;
    }

    @Override
    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    @Override
    public int getCompensation() {
        return mCompensation;
    }

    @Override
    public Matrix getCompensationMatrix() {
        return mCompensationMatrix;
    }

    @Override
    public void freeze() {
        mRenderLock.lock();
        mFreeze = true;
        mRenderLock.unlock();
    }

    @Override
    public void unfreeze() {
        mRenderLock.lock();
        mFreeze = false;
        mFreezeCondition.signalAll();
        mRenderLock.unlock();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void setLightsOutMode(boolean enabled) {
        if (!ApiHelper.HAS_SET_SYSTEM_UI_VISIBILITY) return;

        int flags = 0;
        if (enabled) {
            flags = STATUS_BAR_HIDDEN;
            if (ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) {
                flags |= (SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
        setSystemUiVisibility(flags);
    }

    // We need to unfreeze in the following methods and in onPause().
    // These methods will wait on GLThread. If we have freezed the GLRootView,
    // the GLThread will wait on main thread to call unfreeze and cause dead
    // lock.
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        unfreeze();
        super.surfaceChanged(holder, format, w, h);
        applySurfaceFrameRate(holder);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        unfreeze();
        super.surfaceCreated(holder);
        applySurfaceFrameRate(holder);
    }

    private void applySurfaceFrameRate(SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }
        Display display = getDisplay();
        float hz = ReadingRefreshRate.resolveHz(display, Settings.getReadingRefreshRateHz());
        surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        unfreeze();
        super.surfaceDestroyed(holder);
    }

    @Override
    protected void onDetachedFromWindow() {
        unfreeze();
        super.onDetachedFromWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchSaveInstanceState(@NonNull SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        if (mContentView != null) {
            mContentView.saveHierarchyState(container);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchRestoreInstanceState(@NonNull SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(container);
        if (mContentView != null) {
            mContentView.restoreHierarchyState(container);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            unfreeze();
        } finally {
            super.finalize();
        }
    }

    private class GLRootRenderer implements GLSurfaceView.Renderer {

        /**
         * Called when the context is created, possibly after automatic destruction.
         */
        @Override
        public void onSurfaceCreated(GL10 gl1, EGLConfig config) {
            GL11 gl = (GL11) gl1;
            if (mGL != null) {
                // The GL Object has changed
                Log.i(TAG, "GLObject has changed from " + mGL + " to " + gl);
            }
            mRenderLock.lock();
            try {
                mGL = gl;
                mCanvas = mEGLContextClientVersion == 2 ? new GLES20Canvas() : new GLES11Canvas(gl);
                BasicTexture.invalidateAllTextures();
            } finally {
                mRenderLock.unlock();
            }

            if (DEBUG_FPS) {
                setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            } else {
                setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            }
        }

        /**
         * Called when the OpenGL surface is recreated without destroying the
         * context.
         */
        @Override
        public void onSurfaceChanged(GL10 gl1, int width, int height) {
            Log.i(TAG, "onSurfaceChanged: " + width + "x" + height + ", gl10: " + gl1.toString());
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            GalleryUtils.setRenderThread();
            GL11 gl = (GL11) gl1;
            AssertUtils.assertTrue(mGL == gl);

            mCanvas.setSize(width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            AnimationTime.update();

            long t0 = System.nanoTime();

            mRenderLock.lock();

            while (mFreeze) {
                mFreezeCondition.awaitUninterruptibly();
            }

            try {
                onDrawFrameLocked(gl);
            } finally {
                mRenderLock.unlock();
            }

            long t = System.nanoTime();
            long duration = (t - t0) / 1000000;

            if (duration > 50) {
                Log.v(TAG, "--- " + duration + " ---");
            }
        }
    }

    private class IdleRunner implements Runnable {
        // true if the idle runner is in the queue
        private boolean mActive = false;

        @Override
        public void run() {
            OnGLIdleListener listener;
            synchronized (mIdleListeners) {
                mActive = false;
                if (mIdleListeners.isEmpty()) return;
                listener = mIdleListeners.removeFirst();
            }
            mRenderLock.lock();
            boolean keepInQueue;
            try {
                keepInQueue = listener.onGLIdle(mCanvas, mRenderRequested);
            } finally {
                mRenderLock.unlock();
            }
            synchronized (mIdleListeners) {
                if (keepInQueue) mIdleListeners.addLast(listener);
                if (!mRenderRequested && !mIdleListeners.isEmpty()) enable();
            }
        }

        public void enable() {
            // Who gets the flag can add it to the queue
            if (mActive) return;
            mActive = true;
            queueEvent(this);
        }
    }

    // Always choose a verified window config and honor the resolved GLES client version
    private class ConfigChooser implements EGLConfigChooser, EGLContextFactory {

        private final int[] mValue = new int[1];
        private int mLastEglError = EGL10.EGL_SUCCESS;
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 0x0004;
        private static final int EGL_WINDOW_BIT = 0x0004;
        private static final int EGL_NOT_INITIALIZED = 0x3001;

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            if (display == null || display == EGL10.EGL_NO_DISPLAY) {
                throw new IllegalArgumentException("Invalid EGL display");
            }

            // GLSurfaceView 进入这里之前已经在 GL 线程调用过 eglInitialize。
            // 只有 eglChooseConfig 当场返回 EGL_NOT_INITIALIZED 时才补一次初始化。
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return chooseConfigInternal(egl, display);
                } catch (IllegalArgumentException e) {
                    if (attempt == 0 && mLastEglError == EGL_NOT_INITIALIZED
                            && tryInitializeDisplay(egl, display)) {
                        Log.w(TAG, "eglChooseConfig hit EGL_NOT_INITIALIZED, retrying eglInitialize once");
                        continue;
                    }
                    throw e;
                }
            }

            throw new IllegalArgumentException("eglChooseConfig failed after reinitialize retry");
        }

        /**
         * 在当前 GL 线程补一次 eglInitialize。
         * 调用方已经确认 eglChooseConfig 返回了 EGL_NOT_INITIALIZED，这里不再主动 eglTerminate。
         */
        private boolean tryInitializeDisplay(EGL10 egl, EGLDisplay display) {
            int[] version = new int[2];
            boolean initialized = egl.eglInitialize(display, version);
            if (!initialized) {
                int error = egl.eglGetError();
                Log.w(TAG, "eglInitialize retry failed, error: 0x" + Integer.toHexString(error));
            }
            return initialized;
        }

        /**
         * 统计符合 configSpec 的配置数量。
         * 失败时立刻读取 eglGetError；若是 EGL_NOT_INITIALIZED，写入 {@link #mLastEglError} 并返回 -1，
         * 让外层在同一次错误码还没被清掉时决定要不要重试初始化。
         */
        private int countMatchingConfigs(EGL10 egl, EGLDisplay display, int[] configSpec,
                                         int[] numConfig, String label) {
            if (egl.eglChooseConfig(display, configSpec, null, 0, numConfig) && numConfig[0] > 0) {
                return numConfig[0];
            }
            int error = egl.eglGetError();
            if (error == EGL_NOT_INITIALIZED) {
                mLastEglError = error;
                return -1;
            }
            Log.w(TAG, "eglChooseConfig (" + label + ") failed with error: 0x" + Integer.toHexString(error));
            numConfig[0] = 0;
            return 0;
        }

        private EGLConfig chooseConfigInternal(EGL10 egl, EGLDisplay display) {
            mLastEglError = EGL10.EGL_SUCCESS;
            int[] numConfig = new int[1];
            // 优先匹配显式携带 EGL_OPENGL_ES2_BIT 与 EGL_WINDOW_BIT 的配置：
            // 根据 Khronos EGL 规范，省略 EGL_RENDERABLE_TYPE 时默认按 EGL_OPENGL_ES_BIT (ES 1.x) 过滤；
            // 在 PowerVR GX6250 等将纯 ES 1.1 与 ES 2.0 配置分离暴露的 GPU 上，若先尝试不带
            // EGL_RENDERABLE_TYPE 的规格会命中纯 ES 1.1 配置，进而导致创建 ES 2.0 上下文触发 EGL_BAD_CONFIG。
            int[][] specs = new int[][]{
                    new int[]{
                            EGL10.EGL_RED_SIZE, 8,
                            EGL10.EGL_GREEN_SIZE, 8,
                            EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_ALPHA_SIZE, 8,
                            EGL10.EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL10.EGL_NONE
                    },
                    new int[]{
                            EGL10.EGL_RED_SIZE, 5,
                            EGL10.EGL_GREEN_SIZE, 6,
                            EGL10.EGL_BLUE_SIZE, 5,
                            EGL10.EGL_ALPHA_SIZE, 0,
                            EGL10.EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL10.EGL_NONE
                    },
                    new int[]{
                            EGL10.EGL_RED_SIZE, 8,
                            EGL10.EGL_GREEN_SIZE, 8,
                            EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_ALPHA_SIZE, 8,
                            EGL10.EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                            EGL10.EGL_NONE
                    },
                    new int[]{
                            EGL10.EGL_RED_SIZE, 5,
                            EGL10.EGL_GREEN_SIZE, 6,
                            EGL10.EGL_BLUE_SIZE, 5,
                            EGL10.EGL_ALPHA_SIZE, 0,
                            EGL10.EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                            EGL10.EGL_NONE
                    },
                    new int[]{EGL10.EGL_NONE}
            };
            String[] labels = new String[]{"ES2-8888", "ES2-565", "basic-8888", "minimal-565", "default"};

            for (int i = 0; i < specs.length; i++) {
                int count = countMatchingConfigs(egl, display, specs[i], numConfig, labels[i]);
                if (count < 0) {
                    throw new IllegalArgumentException("eglChooseConfig failed with error: 0x" +
                            Integer.toHexString(mLastEglError));
                }
                if (count == 0) {
                    continue;
                }

                EGLConfig[] configs = new EGLConfig[count];
                if (!egl.eglChooseConfig(display, specs[i], configs, count, numConfig)) {
                    int error = egl.eglGetError();
                    mLastEglError = error;
                    throw new IllegalArgumentException("eglChooseConfig#2 failed with error: 0x" +
                            Integer.toHexString(error));
                }

                int actualCount = Math.min(count, numConfig[0]);
                if (actualCount <= 0) {
                    continue;
                }

                EGLConfig config = chooseVerifiedConfig(egl, display, configs, actualCount);
                if (config != null) {
                    return config;
                }
            }

            throw new IllegalArgumentException("No working EGLConfig matched any configSpec");
        }

        private EGLConfig chooseVerifiedConfig(EGL10 egl, EGLDisplay display,
                                               EGLConfig[] configs, int count) {
            int[] idx = new int[count];
            int[] scores = new int[count];
            int n = 0;

            for (int i = 0; i < count; i++) {
                final EGLConfig config = configs[i];
                if (config == null) continue;

                final int surf = findConfigAttrib(egl, display, config, EGL10.EGL_SURFACE_TYPE);
                if ((surf & EGL_WINDOW_BIT) == 0) continue;

                final int redSize = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE);
                final int greenSize = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE);
                final int blueSize = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE);
                final int alphaSize = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE);
                if (redSize > 8 || greenSize > 8 || blueSize > 8 || alphaSize > 8) continue;

                final int caveat = findConfigAttrib(egl, display, config, EGL10.EGL_CONFIG_CAVEAT);
                final int renderableType = findConfigAttrib(egl, display, config, EGL10.EGL_RENDERABLE_TYPE);
                final int sampleBuffers = findConfigAttrib(egl, display, config, EGL10.EGL_SAMPLE_BUFFERS);
                final int samples = findConfigAttrib(egl, display, config, EGL10.EGL_SAMPLES);
                final int depth = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE);
                final int stencil = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE);
                final int visualId = findConfigAttrib(egl, display, config, EGL10.EGL_NATIVE_VISUAL_ID);

                int score = 0;
                if (caveat == EGL10.EGL_NONE) score += 20000;
                if ((renderableType & EGL_OPENGL_ES2_BIT) != 0) score += 10000;
                if (redSize == 8 && greenSize == 8 && blueSize == 8 && alphaSize == 8) score += 4000;
                else if (redSize == 8 && greenSize == 8 && blueSize == 8 && alphaSize == 0) score += 3500;
                else score += (redSize + greenSize + blueSize + alphaSize) * 50;

                if (visualId == PixelFormat.RGBA_8888) score += 1000;
                else if (visualId == PixelFormat.RGBX_8888) score += 800;

                if (sampleBuffers == 0 && samples == 0) score += 500;
                else score -= samples * 30;

                score -= (depth + stencil);

                idx[n] = i;
                scores[n] = score;
                n++;
            }

            for (int i = 1; i < n; i++) {
                int ki = idx[i], ks = scores[i];
                int j = i - 1;
                while (j >= 0 && scores[j] < ks) {
                    idx[j + 1] = idx[j];
                    scores[j + 1] = scores[j];
                    j--;
                }
                idx[j + 1] = ki;
                scores[j + 1] = ks;
            }

            // 优先验证能否真实创建 OpenGL ES 2.0 上下文；若均不支持则验证 ES 1.1 回退
            for (int k = 0; k < n; k++) {
                EGLConfig cand = configs[idx[k]];
                int rt = findConfigAttrib(egl, display, cand, EGL10.EGL_RENDERABLE_TYPE);
                if ((rt & EGL_OPENGL_ES2_BIT) != 0 && canCreateContext(egl, display, cand, 2)) {
                    mEGLContextClientVersion = 2;
                    return cand;
                }
            }
            for (int k = 0; k < n; k++) {
                EGLConfig cand = configs[idx[k]];
                if (canCreateContext(egl, display, cand, 1)) {
                    mEGLContextClientVersion = 1;
                    Log.w(TAG, "Fell back to verified OpenGL ES 1.1 config");
                    return cand;
                }
            }
            return null;
        }

        private boolean canCreateContext(EGL10 egl, EGLDisplay display, EGLConfig config, int version) {
            int[] attribs = (version == 2)
                    ? new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE}
                    : null;
            EGLContext ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribs);
            if (ctx != null && ctx != EGL10.EGL_NO_CONTEXT) {
                egl.eglDestroyContext(display, ctx);
                return true;
            }
            egl.eglGetError();
            return false;
        }

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            // GLSurfaceView 内部 mEGLContextClientVersion 为私有字段，GLRootView 同名字段无法覆盖父类行为；
            // 由自定义 EGLContextFactory 按实际选定的版本创建上下文，确保 ES 2.0 与 ES 1.1 回退均真正生效。
            int ver = mEGLContextClientVersion;
            int[] attribs = (ver == 2)
                    ? new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE}
                    : null;
            EGLContext ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribs);
            if (ctx != null && ctx != EGL10.EGL_NO_CONTEXT) {
                return ctx;
            }
            if (ver == 2) {
                EGLContext ctx1 = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, null);
                if (ctx1 != null && ctx1 != EGL10.EGL_NO_CONTEXT) {
                    mEGLContextClientVersion = 1;
                    return ctx1;
                }
            }
            return EGL10.EGL_NO_CONTEXT;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e(TAG, "eglDestroyContext failed: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display,
                                     EGLConfig config, int attribute) {
            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
                return mValue[0];
            }
            return 0;
        }
    }
}
