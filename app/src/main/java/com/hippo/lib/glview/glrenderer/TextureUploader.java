/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.hippo.lib.glview.view.GLRoot;

import java.util.ArrayDeque;

public class TextureUploader implements GLRoot.OnGLIdleListener {
    private static final int INIT_CAPACITY = 64;
    private static final int QUOTA_PER_FRAME = 1;

    private final ArrayDeque<UploadedTexture> mFgTextures =
            new ArrayDeque<>(INIT_CAPACITY);
    private final ArrayDeque<UploadedTexture> mBgTextures =
            new ArrayDeque<>(INIT_CAPACITY);
    private final GLRoot mGLRoot;
    private volatile boolean mIsQueued = false;

    public TextureUploader(GLRoot root) {
        mGLRoot = root;
    }

    public synchronized void clear() {
        while (!mFgTextures.isEmpty()) {
            mFgTextures.pop().setIsUploading(false);
        }
        while (!mBgTextures.isEmpty()) {
            mBgTextures.pop().setIsUploading(false);
        }
    }

    // caller should hold synchronized on "this"
    private void queueSelfIfNeed() {
        if (mIsQueued) return;
        mIsQueued = true;
        mGLRoot.addOnGLIdleListener(this);
    }

    public synchronized void addBgTexture(UploadedTexture t) {
        if (t.isContentValid()) return;
        mBgTextures.addLast(t);
        t.setIsUploading(true);
        queueSelfIfNeed();
    }

    public synchronized void addFgTexture(UploadedTexture t) {
        if (t.isContentValid()) return;
        mFgTextures.addLast(t);
        t.setIsUploading(true);
        queueSelfIfNeed();
    }

    private int upload(GLCanvas canvas, ArrayDeque<UploadedTexture> deque,
            int uploadQuota, boolean isBackground) {
        while (uploadQuota > 0) {
            UploadedTexture t;
            synchronized (this) {
                if (deque.isEmpty()) break;
                t = deque.removeFirst();
                t.setIsUploading(false);
                if (t.isContentValid()) continue;

                // this has to be protected by the synchronized block
                // to prevent the inner bitmap get recycled
                t.updateContent(canvas);
            }

            // It will took some more time for a texture to be drawn for
            // the first time.
            // Thus, when scrolling, if a new column appears on screen,
            // it may cause a UI jank even these textures are uploaded.
            if (isBackground) t.draw(canvas, 0, 0);
            --uploadQuota;
        }
        return uploadQuota;
    }

    @Override
    public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
        int uploadQuota = QUOTA_PER_FRAME;
        uploadQuota = upload(canvas, mFgTextures, uploadQuota, false);
        if (uploadQuota < QUOTA_PER_FRAME) mGLRoot.requestRender();
        upload(canvas, mBgTextures, uploadQuota, true);
        synchronized (this) {
            mIsQueued = !mFgTextures.isEmpty() || !mBgTextures.isEmpty();
            return mIsQueued;
        }
    }
}
