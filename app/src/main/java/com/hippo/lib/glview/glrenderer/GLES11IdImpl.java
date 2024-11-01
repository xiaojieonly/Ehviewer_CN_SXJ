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

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

/**
 * Open GL ES 1.1 implementation for generating and destroying texture IDs and
 * buffer IDs
 */
public class GLES11IdImpl implements GLId {

    // Mutex for sNextId
    private final static Object sLock = new Object();
    private static int sNextId = 1;

    @Override
    public int generateTexture() {
        synchronized (sLock) {
            return sNextId++;
        }
    }

    @Override
    public void glGenBuffers(int n, int[] buffers, int offset) {
        synchronized (sLock) {
            while (n-- > 0) {
                buffers[offset + n] = sNextId++;
            }
        }
    }

    @Override
    public void glDeleteTextures(GL11 gl, int n, int[] textures, int offset) {
        synchronized (sLock) {
            gl.glDeleteTextures(n, textures, offset);
        }
    }

    @Override
    public void glDeleteBuffers(GL11 gl, int n, int[] buffers, int offset) {
        synchronized (sLock) {
            gl.glDeleteBuffers(n, buffers, offset);
        }
    }

    @Override
    public void glDeleteFramebuffers(GL11ExtensionPack gl11ep, int n, int[] buffers, int offset) {
        synchronized (sLock) {
            gl11ep.glDeleteFramebuffersOES(n, buffers, offset);
        }
    }
}
