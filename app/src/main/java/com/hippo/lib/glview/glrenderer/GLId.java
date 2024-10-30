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

// This mimics corresponding GL functions.
public interface GLId {
    int generateTexture();

    void glGenBuffers(int n, int[] buffers, int offset);

    void glDeleteTextures(GL11 gl, int n, int[] textures, int offset);

    void glDeleteBuffers(GL11 gl, int n, int[] buffers, int offset);

    void glDeleteFramebuffers(GL11ExtensionPack gl11ep, int n, int[] buffers, int offset);
}
