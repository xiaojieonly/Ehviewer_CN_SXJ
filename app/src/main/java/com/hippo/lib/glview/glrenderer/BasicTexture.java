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

package com.hippo.lib.glview.glrenderer;

import android.graphics.RectF;
import android.util.Log;

import com.hippo.lib.yorozuya.MathUtils;

import java.util.WeakHashMap;

// BasicTexture is a Texture corresponds to a real GL texture.
// The state of a BasicTexture indicates whether its data is loaded to GL memory.
// If a BasicTexture is loaded into GL memory, it has a GL texture id.
public abstract class BasicTexture implements Texture {

    private static final String TAG = "BasicTexture";
    protected static final int UNSPECIFIED = -1;

    protected static final int STATE_UNLOADED = 0;
    protected static final int STATE_LOADED = 1;
    protected static final int STATE_ERROR = -1;

    // Log a warning if a texture is larger along a dimension
    private static final int MAX_TEXTURE_SIZE = 4096;

    protected int mId = -1;
    protected int mState;

    protected int mWidth = UNSPECIFIED;
    protected int mHeight = UNSPECIFIED;

    protected int mTextureWidth;
    protected int mTextureHeight;

    private boolean mHasBorder;

    protected GLCanvas mCanvasRef = null;
    private final static WeakHashMap<BasicTexture, Object> sAllTextures
            = new WeakHashMap<>();
    private static final ThreadLocal<Class> sInFinalizer = new ThreadLocal<>();

    protected BasicTexture(GLCanvas canvas, int id, int state) {
        setAssociatedCanvas(canvas);
        mId = id;
        mState = state;
        synchronized (sAllTextures) {
            sAllTextures.put(this, null);
        }
    }

    protected BasicTexture() {
        this(null, 0, STATE_UNLOADED);
    }

    protected void setAssociatedCanvas(GLCanvas canvas) {
        mCanvasRef = canvas;
    }

    /**
     * Sets the content size of this texture. In OpenGL, the actual texture
     * size must be of power of 2, the size of the content may be smaller.
     */
    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
        mTextureWidth = width > 0 ? MathUtils.nextPowerOf2(width) : 0;
        mTextureHeight = height > 0 ? MathUtils.nextPowerOf2(height) : 0;
        if (mTextureWidth > MAX_TEXTURE_SIZE || mTextureHeight > MAX_TEXTURE_SIZE) {
            Log.w(TAG, String.format("texture is too large: %d x %d",
                    mTextureWidth, mTextureHeight), new Exception());
        }
    }

    public boolean isFlippedVertically() {
      return false;
    }

    public int getId() {
        return mId;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    // Returns the width rounded to the next power of 2.
    public int getTextureWidth() {
        return mTextureWidth;
    }

    // Returns the height rounded to the next power of 2.
    public int getTextureHeight() {
        return mTextureHeight;
    }

    // Returns true if the texture has one pixel transparent border around the
    // actual content. This is used to avoid jigged edges.
    //
    // The jigged edges appear because we use GL_CLAMP_TO_EDGE for texture wrap
    // mode (GL_CLAMP is not available in OpenGL ES), so a pixel partially
    // covered by the texture will use the color of the edge texel. If we add
    // the transparent border, the color of the edge texel will be mixed with
    // appropriate amount of transparent.
    //
    // Currently our background is black, so we can draw the thumbnails without
    // enabling blending.
    public boolean hasBorder() {
        return mHasBorder;
    }

    protected void setBorder(boolean hasBorder) {
        mHasBorder = hasBorder;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y) {
        canvas.drawTexture(this, x, y, getWidth(), getHeight());
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        canvas.drawTexture(this, x, y, w, h);
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF target) {
        canvas.drawTexture(this, source, target);
    }

    // onBind is called before GLCanvas binds this texture.
    // It should make sure the data is uploaded to GL memory.
    abstract protected boolean onBind(GLCanvas canvas);

    // Returns the GL texture target for this texture (e.g. GL_TEXTURE_2D).
    abstract protected int getTarget();

    public boolean isLoaded() {
        return mState == STATE_LOADED;
    }

    // recycle() is called when the texture will never be used again,
    // so it can free all resources.
    public void recycle() {
        freeResource();
    }

    // yield() is called when the texture will not be used temporarily,
    // so it can free some resources.
    // The default implementation unloads the texture from GL memory, so
    // the subclass should make sure it can reload the texture to GL memory
    // later, or it will have to override this method.
    public void yield() {
        freeResource();
    }

    private void freeResource() {
        GLCanvas canvas = mCanvasRef;
        if (canvas != null && mId != -1) {
            canvas.unloadTexture(this);
            mId = -1; // Don't free it again.
        }
        mState = STATE_UNLOADED;
        setAssociatedCanvas(null);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            sInFinalizer.set(BasicTexture.class);
            recycle();
            sInFinalizer.set(null);
        } finally {
            super.finalize();
        }
    }

    // This is for deciding if we can call Bitmap's recycle().
    // We cannot call Bitmap's recycle() in finalizer because at that point
    // the finalizer of Bitmap may already be called so recycle() will crash.
    public static boolean inFinalizer() {
        return sInFinalizer.get() != null;
    }

    public static void yieldAllTextures() {
        synchronized (sAllTextures) {
            for (BasicTexture t : sAllTextures.keySet()) {
                t.yield();
            }
        }
    }

    public static void invalidateAllTextures() {
        synchronized (sAllTextures) {
            for (BasicTexture t : sAllTextures.keySet()) {
                t.mState = STATE_UNLOADED;
                t.setAssociatedCanvas(null);
            }
        }
    }
}
