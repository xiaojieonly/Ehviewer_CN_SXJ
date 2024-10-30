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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

//
// GLCanvas gives a convenient interface to draw using OpenGL.
//
// When a rectangle is specified in this interface, it means the region
// [x, x+width) * [y, y+height)
//
public interface GLCanvas {

    GLId getGLId();

    // Tells GLCanvas the size of the underlying GL surface. This should be
    // called before first drawing and when the size of GL surface is changed.
    // This is called by GLRoot and should not be called by the clients
    // who only want to draw on the GLCanvas. Both width and height must be
    // nonnegative.
    void setSize(int width, int height);

    // Clear the drawing buffers. This should only be used by GLRoot.
    void clearBuffer();

    void clearBuffer(float[] argb);

    // Sets and gets the current alpha, alpha must be in [0, 1].
    void setAlpha(float alpha);

    float getAlpha();

    // (current alpha) = (current alpha) * alpha
    void multiplyAlpha(float alpha);

    // Change the current transform matrix.
    void translate(float x, float y, float z);

    void translate(float x, float y);

    void scale(float sx, float sy, float sz);

    void rotate(float angle, float x, float y, float z);

    void multiplyMatrix(float[] mMatrix, int offset);

    // Pushes the configuration state (matrix, and alpha) onto
    // a private stack.
    void save();

    // Same as save(), but only save those specified in saveFlags.
    void save(int saveFlags);

    int SAVE_FLAG_ALL = 0xFFFFFFFF;
    int SAVE_FLAG_ALPHA = 0x01;
    int SAVE_FLAG_MATRIX = 0x02;

    // Pops from the top of the stack as current configuration state (matrix,
    // alpha, and clip). This call balances a previous call to save(), and is
    // used to remove all modifications to the configuration state since the
    // last save call.
    void restore();

    // Draws a line using the specified paint from (x1, y1) to (x2, y2).
    // (Both end points are included).
    void drawLine(float x1, float y1, float x2, float y2, GLPaint paint);

    // Draws a rectangle using the specified paint from (x1, y1) to (x2, y2).
    // (Both end points are included).
    void drawRect(float x1, float y1, float x2, float y2, GLPaint paint);

    // Draws a oval using the specified paint for (cx, cy, radiusX, radiusY)
    // (Both end points are included).
    void drawOval(float cx, float cy, float radiusX, float radiusY,
            GLPaint paint);

    // Draw a arc inside a rect
    void drawArc(float cx, float cy, float radiusX, float radiusY,
            float sweepAngle, GLPaint paint);

    // Fills the specified rectangle with the specified color.
    void fillRect(float x, float y, float width, float height, int color);

    // Fills the specified oval with the specified color.
    void fillOval(float cx, float cy, float radiusX, float radiusY, int color);

    // Fills the specified sector with the specified color.
    void fillSector(float cx, float cy, float radiusX, float radiusY, float sweepAngle, int color);

    // Draws a texture to the specified rectangle.
    void drawTexture(BasicTexture texture, int x, int y, int width, int height);

    void drawMesh(BasicTexture tex, int x, int y, int xyBuffer, int uvBuffer, int indexBuffer, int indexCount);

    // Draws the source rectangle part of the texture to the target rectangle.
    void drawTexture(BasicTexture texture, RectF source, RectF target);

    // Draw a texture with a specified texture transform.
    void drawTexture(BasicTexture texture, float[] mTextureTransform, int x, int y, int w, int h);

    // Draw two textures to the specified rectangle. The actual texture used is
    // from * (1 - ratio) + to * ratio
    // The two textures must have the same size.
    void drawMixed(BasicTexture from, int toColor, float ratio, int x, int y, int w, int h);

    // Draw a region of a texture and a specified color to the specified
    // rectangle. The actual color used is from * (1 - ratio) + to * ratio.
    // The region of the texture is defined by parameter "src". The target
    // rectangle is specified by parameter "target".
    void drawMixed(BasicTexture from, int toColor, float ratio, RectF src, RectF target);

    // Unloads the specified texture from the canvas. The resource allocated
    // to draw the texture will be released. The specified texture will return
    // to the unloaded state. This function should be called only from
    // BasicTexture or its descendant
    boolean unloadTexture(BasicTexture texture);

    // Delete the specified buffer object, similar to unloadTexture.
    void deleteBuffer(int bufferId);

    // Delete the textures and buffers in GL side. This function should only be
    // called in the GL thread.
    void deleteRecycledResources();

    // Dump statistics information and clear the counters. For debug only.
    void dumpStatisticsAndClear();

    void beginRenderTarget(RawTexture texture);

    void endRenderTarget();

    /**
     * Sets texture parameters to use GL_CLAMP_TO_EDGE for both
     * GL_TEXTURE_WRAP_S and GL_TEXTURE_WRAP_T. Sets texture parameters to be
     * GL_LINEAR for GL_TEXTURE_MIN_FILTER and GL_TEXTURE_MAG_FILTER.
     * bindTexture() must be called prior to this.
     *
     * @param texture The texture to set parameters on.
     */
    void setTextureParameters(BasicTexture texture);

    /**
     * Initializes the texture to a size by calling texImage2D on it.
     *
     * @param texture The texture to initialize the size.
     * @param format The texture format (e.g. GL_RGBA)
     * @param type The texture type (e.g. GL_UNSIGNED_BYTE)
     */
    void initializeTextureSize(BasicTexture texture, int format, int type);

    /**
     * Initializes the texture to a size by calling texImage2D on it.
     *
     * @param texture The texture to initialize the size.
     * @param bitmap The bitmap to initialize the bitmap with.
     */
    void initializeTexture(BasicTexture texture, Bitmap bitmap);

    /**
     * Calls glTexSubImage2D to upload a bitmap to the texture.
     *
     * @param texture The target texture to write to.
     * @param xOffset Specifies a texel offset in the x direction within the
     *            texture array.
     * @param yOffset Specifies a texel offset in the y direction within the
     *            texture array.
     * @param format The texture format (e.g. GL_RGBA)
     * @param type The texture type (e.g. GL_UNSIGNED_BYTE)
     */
    void texSubImage2D(BasicTexture texture, int xOffset, int yOffset, Bitmap bitmap, int format, int type);

    /**
     * Generates buffers and uploads the buffer data.
     *
     * @param buffer The buffer to upload
     * @return The buffer ID that was generated.
     */
    int uploadBuffer(java.nio.FloatBuffer buffer);

    /**
     * Generates buffers and uploads the element array buffer data.
     *
     * @param buffer The buffer to upload
     * @return The buffer ID that was generated.
     */
    int uploadBuffer(java.nio.ByteBuffer buffer);

    /**
     * After LightCycle makes GL calls, this method is called to restore the GL
     * configuration to the one expected by GLCanvas.
     */
    void recoverFromLightCycle();

    /**
     * Gets the bounds given by x, y, width, and height as well as the internal
     * matrix state. There is no special handling for non-90-degree rotations.
     * It only considers the lower-left and upper-right corners as the bounds.
     *
     * @param bounds The output bounds to write to.
     * @param x The left side of the input rectangle.
     * @param y The bottom of the input rectangle.
     * @param width The width of the input rectangle.
     * @param height The height of the input rectangle.
     */
    void getBounds(Rect bounds, int x, int y, int width, int height);
}
