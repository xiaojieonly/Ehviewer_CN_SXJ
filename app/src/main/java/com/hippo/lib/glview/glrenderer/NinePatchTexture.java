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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import com.hippo.lib.yorozuya.MathUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

// NinePatchTexture is a texture backed by a NinePatch resource.
//
// getPaddings() returns paddings specified in the NinePatch.
// getNinePatchChunk() returns the layout data specified in the NinePatch.
//
public class NinePatchTexture extends ResourceTexture {
    @SuppressWarnings("unused")
    private static final String TAG = "NinePatchTexture";
    private NinePatchChunk mChunk;
    private SmallCache<NinePatchInstance> mInstanceCache
            = new SmallCache<NinePatchInstance>();

    public NinePatchTexture(Context context, int resId) {
        super(context, resId);
    }

    @Override
    protected Bitmap onGetBitmap() {
        if (mBitmap != null) return mBitmap;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
        mBitmap = bitmap;
        setSize(bitmap.getWidth(), bitmap.getHeight());
        byte[] chunkData = bitmap.getNinePatchChunk();
        mChunk = chunkData == null
                ? null
                : NinePatchChunk.deserialize(bitmap.getNinePatchChunk());
        if (mChunk == null) {
            throw new RuntimeException("invalid nine-patch image: " + mResId);
        }
        return bitmap;
    }

    public Rect getPaddings() {
        // get the paddings from nine patch
        if (mChunk == null) onGetBitmap();
        return mChunk.mPaddings;
    }

    public NinePatchChunk getNinePatchChunk() {
        if (mChunk == null) onGetBitmap();
        return mChunk;
    }

    // This is a simple cache for a small number of things. Linear search
    // is used because the cache is small. It also tries to remove less used
    // item when the cache is full by moving the often-used items to the front.
    private static class SmallCache<V> {
        private static final int CACHE_SIZE = 16;
        private static final int CACHE_SIZE_START_MOVE = CACHE_SIZE / 2;
        private int[] mKey = new int[CACHE_SIZE];
        private V[] mValue = (V[]) new Object[CACHE_SIZE];
        private int mCount;  // number of items in this cache

        // Puts a value into the cache. If the cache is full, also returns
        // a less used item, otherwise returns null.
        public V put(int key, V value) {
            if (mCount == CACHE_SIZE) {
                V old = mValue[CACHE_SIZE - 1];  // remove the last item
                mKey[CACHE_SIZE - 1] = key;
                mValue[CACHE_SIZE - 1] = value;
                return old;
            } else {
                mKey[mCount] = key;
                mValue[mCount] = value;
                mCount++;
                return null;
            }
        }

        public V get(int key) {
            for (int i = 0; i < mCount; i++) {
                if (mKey[i] == key) {
                    // Move the accessed item one position to the front, so it
                    // will less likely to be removed when cache is full. Only
                    // do this if the cache is starting to get full.
                    if (mCount > CACHE_SIZE_START_MOVE && i > 0) {
                        int tmpKey = mKey[i];
                        mKey[i] = mKey[i - 1];
                        mKey[i - 1] = tmpKey;

                        V tmpValue = mValue[i];
                        mValue[i] = mValue[i - 1];
                        mValue[i - 1] = tmpValue;
                    }
                    return mValue[i];
                }
            }
            return null;
        }

        public void clear() {
            for (int i = 0; i < mCount; i++) {
                mValue[i] = null;  // make sure it's can be garbage-collected.
            }
            mCount = 0;
        }

        public int size() {
            return mCount;
        }

        public V valueAt(int i) {
            return mValue[i];
        }
    }

    private NinePatchInstance findInstance(GLCanvas canvas, int w, int h) {
        int key = w;
        key = (key << 16) | h;
        NinePatchInstance instance = mInstanceCache.get(key);

        if (instance == null) {
            instance = new NinePatchInstance(this, w, h);
            NinePatchInstance removed = mInstanceCache.put(key, instance);
            if (removed != null) {
                removed.recycle(canvas);
            }
        }

        return instance;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        if (!isLoaded()) {
            mInstanceCache.clear();
        }

        if (w != 0 && h != 0) {
            findInstance(canvas, w, h).draw(canvas, this, x, y);
        }
    }

    @Override
    public void recycle() {
        super.recycle();
        GLCanvas canvas = mCanvasRef;
        if (canvas == null) return;
        int n = mInstanceCache.size();
        for (int i = 0; i < n; i++) {
            NinePatchInstance instance = mInstanceCache.valueAt(i);
            instance.recycle(canvas);
        }
        mInstanceCache.clear();
    }
}

// This keeps data for a specialization of NinePatchTexture with the size
// (width, height). We pre-compute the coordinates for efficiency.
class NinePatchInstance {

    @SuppressWarnings("unused")
    private static final String TAG = "NinePatchInstance";

    // We need 16 vertices for a normal nine-patch image (the 4x4 vertices)
    private static final int VERTEX_BUFFER_SIZE = 16 * 2;

    // We need 22 indices for a normal nine-patch image, plus 2 for each
    // transparent region. Current there are at most 1 transparent region.
    private static final int INDEX_BUFFER_SIZE = 22 + 2;

    private FloatBuffer mXyBuffer;
    private FloatBuffer mUvBuffer;
    private ByteBuffer mIndexBuffer;

    // Names for buffer names: xy, uv, index.
    private int mXyBufferName = -1;
    private int mUvBufferName;
    private int mIndexBufferName;

    private int mIdxCount;

    public NinePatchInstance(NinePatchTexture tex, int width, int height) {
        NinePatchChunk chunk = tex.getNinePatchChunk();

        if (width <= 0 || height <= 0) {
            throw new RuntimeException("invalid dimension");
        }

        // The code should be easily extended to handle the general cases by
        // allocating more space for buffers. But let's just handle the only
        // use case.
        if (chunk.mDivX.length != 2 || chunk.mDivY.length != 2) {
            throw new RuntimeException("unsupported nine patch");
        }

        float divX[] = new float[4];
        float divY[] = new float[4];
        float divU[] = new float[4];
        float divV[] = new float[4];

        int nx = stretch(divX, divU, chunk.mDivX, tex.getWidth(), width);
        int ny = stretch(divY, divV, chunk.mDivY, tex.getHeight(), height);

        prepareVertexData(divX, divY, divU, divV, nx, ny, chunk.mColor);
    }

    /**
     * Stretches the texture according to the nine-patch rules. It will
     * linearly distribute the strechy parts defined in the nine-patch chunk to
     * the target area.
     *
     * <pre>
     *                      source
     *          /--------------^---------------\
     *         u0    u1       u2  u3     u4   u5
     * div ---> |fffff|ssssssss|fff|ssssss|ffff| ---> u
     *          |    div0    div1 div2   div3  |
     *          |     |       /   /      /    /
     *          |     |      /   /     /    /
     *          |     |     /   /    /    /
     *          |fffff|ssss|fff|sss|ffff| ---> x
     *         x0    x1   x2  x3  x4   x5
     *          \----------v------------/
     *                  target
     *
     * f: fixed segment
     * s: stretchy segment
     * </pre>
     *
     * @param div the stretch parts defined in nine-patch chunk
     * @param source the length of the texture
     * @param target the length on the drawing plan
     * @param u output, the positions of these dividers in the texture
     *        coordinate
     * @param x output, the corresponding position of these dividers on the
     *        drawing plan
     * @return the number of these dividers.
     */
    private static int stretch(
            float x[], float u[], int div[], int source, int target) {
        int textureSize = MathUtils.nextPowerOf2(source);
        float textureBound = (float) source / textureSize;

        float stretch = 0;
        for (int i = 0, n = div.length; i < n; i += 2) {
            stretch += div[i + 1] - div[i];
        }

        float remaining = target - source + stretch;

        float lastX = 0;
        float lastU = 0;

        x[0] = 0;
        u[0] = 0;
        for (int i = 0, n = div.length; i < n; i += 2) {
            // Make the stretchy segment a little smaller to prevent sampling
            // on neighboring fixed segments.
            // fixed segment
            x[i + 1] = lastX + (div[i] - lastU) + 0.5f;
            u[i + 1] = Math.min((div[i] + 0.5f) / textureSize, textureBound);

            // stretchy segment
            float partU = div[i + 1] - div[i];
            float partX = remaining * partU / stretch;
            remaining -= partX;
            stretch -= partU;

            lastX = x[i + 1] + partX;
            lastU = div[i + 1];
            x[i + 2] = lastX - 0.5f;
            u[i + 2] = Math.min((lastU - 0.5f)/ textureSize, textureBound);
        }
        // the last fixed segment
        x[div.length + 1] = target;
        u[div.length + 1] = textureBound;

        // remove segments with length 0.
        int last = 0;
        for (int i = 1, n = div.length + 2; i < n; ++i) {
            if ((x[i] - x[last]) < 1f) continue;
            x[++last] = x[i];
            u[last] = u[i];
        }
        return last + 1;
    }

    private void prepareVertexData(float x[], float y[], float u[], float v[],
            int nx, int ny, int[] color) {
        /*
         * Given a 3x3 nine-patch image, the vertex order is defined as the
         * following graph:
         *
         * (0) (1) (2) (3)
         *  |  /|  /|  /|
         *  | / | / | / |
         * (4) (5) (6) (7)
         *  | \ | \ | \ |
         *  |  \|  \|  \|
         * (8) (9) (A) (B)
         *  |  /|  /|  /|
         *  | / | / | / |
         * (C) (D) (E) (F)
         *
         * And we draw the triangle strip in the following index order:
         *
         * index: 04152637B6A5948C9DAEBF
         */
        int pntCount = 0;
        float xy[] = new float[VERTEX_BUFFER_SIZE];
        float uv[] = new float[VERTEX_BUFFER_SIZE];
        for (int j = 0; j < ny; ++j) {
            for (int i = 0; i < nx; ++i) {
                int xIndex = (pntCount++) << 1;
                int yIndex = xIndex + 1;
                xy[xIndex] = x[i];
                xy[yIndex] = y[j];
                uv[xIndex] = u[i];
                uv[yIndex] = v[j];
            }
        }

        int idxCount = 1;
        boolean isForward = false;
        byte index[] = new byte[INDEX_BUFFER_SIZE];
        for (int row = 0; row < ny - 1; row++) {
            --idxCount;
            isForward = !isForward;

            int start, end, inc;
            if (isForward) {
                start = 0;
                end = nx;
                inc = 1;
            } else {
                start = nx - 1;
                end = -1;
                inc = -1;
            }

            for (int col = start; col != end; col += inc) {
                int k = row * nx + col;
                if (col != start) {
                    int colorIdx = row * (nx - 1) + col;
                    if (isForward) colorIdx--;
                    if (color[colorIdx] == NinePatchChunk.TRANSPARENT_COLOR) {
                        index[idxCount] = index[idxCount - 1];
                        ++idxCount;
                        index[idxCount++] = (byte) k;
                    }
                }

                index[idxCount++] = (byte) k;
                index[idxCount++] = (byte) (k + nx);
            }
        }

        mIdxCount = idxCount;

        int size = (pntCount * 2) * (Float.SIZE / Byte.SIZE);
        mXyBuffer = allocateDirectNativeOrderBuffer(size).asFloatBuffer();
        mUvBuffer = allocateDirectNativeOrderBuffer(size).asFloatBuffer();
        mIndexBuffer = allocateDirectNativeOrderBuffer(mIdxCount);

        mXyBuffer.put(xy, 0, pntCount * 2).position(0);
        mUvBuffer.put(uv, 0, pntCount * 2).position(0);
        mIndexBuffer.put(index, 0, idxCount).position(0);
    }

    private static ByteBuffer allocateDirectNativeOrderBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private void prepareBuffers(GLCanvas canvas) {
        mXyBufferName = canvas.uploadBuffer(mXyBuffer);
        mUvBufferName = canvas.uploadBuffer(mUvBuffer);
        mIndexBufferName = canvas.uploadBuffer(mIndexBuffer);

        // These buffers are never used again.
        mXyBuffer = null;
        mUvBuffer = null;
        mIndexBuffer = null;
    }

    public void draw(GLCanvas canvas, NinePatchTexture tex, int x, int y) {
        if (mXyBufferName == -1) {
            prepareBuffers(canvas);
        }
        canvas.drawMesh(tex, x, y, mXyBufferName, mUvBufferName, mIndexBufferName, mIdxCount);
    }

    public void recycle(GLCanvas canvas) {
        if (mXyBuffer == null) {
            canvas.deleteBuffer(mXyBufferName);
            canvas.deleteBuffer(mUvBufferName);
            canvas.deleteBuffer(mIndexBufferName);
            mXyBufferName = -1;
        }
    }
}
