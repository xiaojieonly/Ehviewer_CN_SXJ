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

// ExtTexture is a texture whose content comes from a external texture.
// Before drawing, setSize() should be called.
public class ExtTexture extends BasicTexture {

    private final int mTarget;

    public ExtTexture(GLCanvas canvas, int target) {
        GLId glId = canvas.getGLId();
        mId = glId.generateTexture();
        mTarget = target;
    }

    private void uploadToCanvas(GLCanvas canvas) {
        canvas.setTextureParameters(this);
        setAssociatedCanvas(canvas);
        mState = STATE_LOADED;
    }

    @Override
    protected boolean onBind(GLCanvas canvas) {
        if (!isLoaded()) {
            uploadToCanvas(canvas);
        }

        return true;
    }

    @Override
    public int getTarget() {
        return mTarget;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    public void yield() {
        // we cannot free the texture because we have no backup.
    }
}
