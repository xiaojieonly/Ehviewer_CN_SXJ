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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.collect.IntList;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

public class GLES11Canvas implements GLCanvas {
    @SuppressWarnings("unused")
    private static final String TAG = "GLCanvasImp";

    private static final float OPAQUE_ALPHA = 0.95f;

    private static final int COUNT_FILL_VERTEX = 4;
    private static final int COUNT_LINE_VERTEX = 2;
    private static final int COUNT_RECT_VERTEX = 4;
    private static final int COUNT_CIRCLE_VERTEX = 120; // multiple of 4
    private static final int OFFSET_FILL_RECT = 0;
    private static final int OFFSET_DRAW_LINE = OFFSET_FILL_RECT + COUNT_FILL_VERTEX;
    private static final int OFFSET_DRAW_RECT = OFFSET_DRAW_LINE + COUNT_LINE_VERTEX;
    private static final int OFFSET_FILL_CIRCLE = OFFSET_DRAW_RECT + COUNT_RECT_VERTEX;
    private static final int OFFSET_DRAW_CIRCLE = OFFSET_FILL_CIRCLE + 1;
    private static final int OFFSET_LAST = OFFSET_DRAW_CIRCLE + COUNT_CIRCLE_VERTEX + 1;

    private static final float[] BOX_COORDINATES = new float[OFFSET_LAST * 2];
    // TODO: the code only work for 2D should get fixed for 3D or removed
    private static final int MSKEW_X = 4;
    private static final int MSKEW_Y = 1;
    private static final int MSCALE_X = 0;
    private static final int MSCALE_Y = 5;
    private static final float[] sCropRect = new float[4];
    private static final GLId mGLId = new GLES11IdImpl();

    static {
        float[] temp = {
                0, 0, // Fill rectangle
                1, 0,
                0, 1,
                1, 1,
                0, 0, // Draw line
                1, 1,
                0, 0, // Draw rectangle outline
                0, 1,
                1, 1,
                1, 0,
                0.5f, 0.5f // Fill circle
        };
        System.arraycopy(temp, 0, BOX_COORDINATES, 0, temp.length);

        // Draw circle
        int arrayOffset = OFFSET_DRAW_CIRCLE * 2;
        for (int i = 0, n = COUNT_CIRCLE_VERTEX / 4; i <= n; i++) {
            float value = (float) Math.sin(MathUtils.radians(90f / n * i)) / 2;
            float positive = value + 0.5f;
            float negative = -value + 0.5f;
            BOX_COORDINATES[arrayOffset + i * 2 + 1] = positive;
            BOX_COORDINATES[arrayOffset + n * 2 - i * 2] = positive;
            BOX_COORDINATES[arrayOffset + n * 2 + i * 2] = negative;
            BOX_COORDINATES[arrayOffset + n * 4 - i * 2 + 1] = positive;
            BOX_COORDINATES[arrayOffset + n * 4 + i * 2 + 1] = negative;
            BOX_COORDINATES[arrayOffset + n * 6 - i * 2] = negative;
            BOX_COORDINATES[arrayOffset + n * 6 + i * 2] = positive;
            BOX_COORDINATES[arrayOffset + n * 8 - i * 2 + 1] = negative;
        }
    }

    private final float[] mMatrixValues = new float[16];
    private final float[] mTextureMatrixValues = new float[16];
    // The results of mapPoints are stored in this buffer, and the order is
    // x1, y1, x2, y2.
    private final float[] mMapPointsBuffer = new float[4];
    private final float[] mTextureColor = new float[4];
    private final ArrayList<RawTexture> mTargetStack = new ArrayList<RawTexture>();
    private final ArrayList<ConfigState> mRestoreStack = new ArrayList<ConfigState>();
    private final RectF mDrawTextureSourceRect = new RectF();
    private final RectF mDrawTextureTargetRect = new RectF();
    private final float[] mTempMatrix = new float[32];
    private final IntList mUnboundTextures = new IntList();
    private final IntList mDeleteBuffers = new IntList();
    private final GL11 mGL;
    private final int mBoxCoords;
    private final GLState mGLState;
    private final boolean mBlendEnabled = true;
    private final int[] mFrameBuffer = new int[1];
    // Drawing statistics
    int mCountDrawLine;
    int mCountFillRect;
    int mCountDrawMesh;
    int mCountTextureRect;
    int mCountTextureOES;
    private float mAlpha;
    private ConfigState mRecycledRestoreAction;
    private int mScreenWidth;
    private int mScreenHeight;
    private RawTexture mTargetTexture;

    public GLES11Canvas(GL11 gl) {
        mGL = gl;
        mGLState = new GLState(gl);
        // First create an nio buffer, then create a VBO from it.
        int size = BOX_COORDINATES.length * Float.SIZE / Byte.SIZE;
        FloatBuffer xyBuffer = allocateDirectNativeOrderBuffer(size).asFloatBuffer();
        xyBuffer.put(BOX_COORDINATES, 0, BOX_COORDINATES.length).position(0);

        int[] name = new int[1];
        mGLId.glGenBuffers(1, name, 0);
        mBoxCoords = name[0];

        gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, mBoxCoords);
        gl.glBufferData(GL11.GL_ARRAY_BUFFER, xyBuffer.capacity() * (Float.SIZE / Byte.SIZE),
                xyBuffer, GL11.GL_STATIC_DRAW);

        gl.glVertexPointer(2, GL11.GL_FLOAT, 0, 0);
        gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);

        // Enable the texture coordinate array for Texture 1
        gl.glClientActiveTexture(GL11.GL_TEXTURE1);
        gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
        gl.glClientActiveTexture(GL11.GL_TEXTURE0);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        // mMatrixValues and mAlpha will be initialized in setSize()
    }

    private static ByteBuffer allocateDirectNativeOrderBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    // This function changes the source coordinate to the texture coordinates.
    // It also clips the source and target coordinates if it is beyond the
    // bound of the texture.
    private static void convertCoordinate(RectF source, RectF target,
                                          BasicTexture texture) {

        int width = texture.getWidth();
        int height = texture.getHeight();
        int texWidth = texture.getTextureWidth();
        int texHeight = texture.getTextureHeight();
        // Convert to texture coordinates
        source.left /= texWidth;
        source.right /= texWidth;
        source.top /= texHeight;
        source.bottom /= texHeight;

        // Clip if the rendering range is beyond the bound of the texture.
        float xBound = (float) width / texWidth;
        if (source.right > xBound) {
            target.right = target.left + target.width() *
                    (xBound - source.left) / source.width();
            source.right = xBound;
        }
        float yBound = (float) height / texHeight;
        if (source.bottom > yBound) {
            target.bottom = target.top + target.height() *
                    (yBound - source.top) / source.height();
            source.bottom = yBound;
        }
    }

    private static boolean isMatrixRotatedOrFlipped(float[] matrix) {
        final float eps = 1e-5f;
        return Math.abs(matrix[MSKEW_X]) > eps
                || Math.abs(matrix[MSKEW_Y]) > eps
                || matrix[MSCALE_X] < -eps
                || matrix[MSCALE_Y] > eps;
    }

    private static void checkFramebufferStatus(GL11ExtensionPack gl11ep) {
        int status = gl11ep.glCheckFramebufferStatusOES(GL11ExtensionPack.GL_FRAMEBUFFER_OES);
        if (status != GL11ExtensionPack.GL_FRAMEBUFFER_COMPLETE_OES) {
            String msg = "";
            switch (status) {
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_FORMATS_OES:
                    msg = "FRAMEBUFFER_FORMATS";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT_OES:
                    msg = "FRAMEBUFFER_ATTACHMENT";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT_OES:
                    msg = "FRAMEBUFFER_MISSING_ATTACHMENT";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER_OES:
                    msg = "FRAMEBUFFER_DRAW_BUFFER";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER_OES:
                    msg = "FRAMEBUFFER_READ_BUFFER";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_UNSUPPORTED_OES:
                    msg = "FRAMEBUFFER_UNSUPPORTED";
                    break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_OES:
                    msg = "FRAMEBUFFER_INCOMPLETE_DIMENSIONS";
                    break;
            }
            throw new RuntimeException(msg + ":" + Integer.toHexString(status));
        }
    }

    @Override
    public void setSize(int width, int height) {
        if (mTargetTexture == null) {
            mScreenWidth = width;
            mScreenHeight = height;
        }
        mAlpha = 1.0f;

        GL11 gl = mGL;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL11.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluOrtho2D(gl, 0, width, 0, height);

        gl.glMatrixMode(GL11.GL_MODELVIEW);
        gl.glLoadIdentity();

        float[] matrix = mMatrixValues;
        Matrix.setIdentityM(matrix, 0);
        // to match the graphic coordinate system in android, we flip it vertically.
        if (mTargetTexture == null) {
            Matrix.translateM(matrix, 0, 0, height, 0);
            Matrix.scaleM(matrix, 0, 1, -1, 1);
        }
    }

    @Override
    public float getAlpha() {
        return mAlpha;
    }

    @Override
    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    @Override
    public void multiplyAlpha(float alpha) {
        mAlpha *= alpha;
    }

    @Override
    public void drawRect(float x, float y, float width, float height, GLPaint paint) {
        GL11 gl = mGL;

        mGLState.setColorMode(paint.getColor(), mAlpha);
        mGLState.setLineWidth(paint.getLineWidth());

        saveTransform();
        translate(x, y);
        scale(width, height, 1);

        gl.glLoadMatrixf(mMatrixValues, 0);
        gl.glDrawArrays(GL11.GL_LINE_LOOP, OFFSET_DRAW_RECT, COUNT_RECT_VERTEX);

        restoreTransform();
        mCountDrawLine++;
    }

    @Override
    public void drawLine(float x1, float y1, float x2, float y2, GLPaint paint) {
        GL11 gl = mGL;

        mGLState.setColorMode(paint.getColor(), mAlpha);
        mGLState.setLineWidth(paint.getLineWidth());

        saveTransform();
        translate(x1, y1);
        scale(x2 - x1, y2 - y1, 1);

        gl.glLoadMatrixf(mMatrixValues, 0);
        gl.glDrawArrays(GL11.GL_LINE_STRIP, OFFSET_DRAW_LINE, COUNT_LINE_VERTEX);

        restoreTransform();
        mCountDrawLine++;
    }

    @Override
    public void drawOval(float cx, float cy, float radiusX, float radiusY, GLPaint paint) {
        float halfLineWidth = paint.getLineWidth() / 2;
        fillOval(cx, cy, radiusX + halfLineWidth, radiusY + halfLineWidth, paint.getColor());
        fillOval(cx, cy, radiusX - halfLineWidth, radiusY - halfLineWidth, paint.getBackgroundColor());
    }

    @Override
    public void drawArc(float cx, float cy, float radiusX, float radiusY, float sweepAngle, GLPaint paint) {
        float halfLineWidth = paint.getLineWidth() / 2;
        fillSector(cx, cy, radiusX + halfLineWidth, radiusY + halfLineWidth, sweepAngle, paint.getColor());
        fillOval(cx, cy, radiusX - halfLineWidth, radiusY - halfLineWidth, paint.getBackgroundColor());
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int color) {
        mGLState.setColorMode(color, mAlpha);
        GL11 gl = mGL;

        saveTransform();
        translate(x, y);
        scale(width, height, 1);

        gl.glLoadMatrixf(mMatrixValues, 0);
        gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, OFFSET_FILL_RECT, COUNT_FILL_VERTEX);

        restoreTransform();
        mCountFillRect++;
    }

    @Override
    public void fillOval(float cx, float cy, float radiusX, float radiusY, int color) {
        mGLState.setColorMode(color, mAlpha);
        GL11 gl = mGL;

        saveTransform();
        translate(cx - radiusX, cy - radiusY);
        scale(radiusX * 2, radiusY * 2, 1);

        gl.glLoadMatrixf(mMatrixValues, 0);
        gl.glDrawArrays(GL11.GL_TRIANGLE_FAN, OFFSET_FILL_CIRCLE, OFFSET_LAST - OFFSET_FILL_CIRCLE);

        restoreTransform();
        mCountFillRect++;
    }

    @Override
    public void fillSector(float cx, float cy, float radiusX, float radiusY, float sweepAngle, int color) {
        float conjugateAngle = Math.abs(360 - MathUtils.positiveModulo(sweepAngle, 360));
        int conjugateCount = Math.round(COUNT_CIRCLE_VERTEX * conjugateAngle / 360);
        if (conjugateCount == 0) {
            // It is a circle
            fillOval(cx, cy, radiusX, radiusY, color);
        } else if (conjugateCount < COUNT_CIRCLE_VERTEX) {
            mGLState.setColorMode(color, mAlpha);
            GL11 gl = mGL;

            saveTransform();
            translate(cx - radiusX, cy - radiusY);
            scale(radiusX * 2, radiusY * 2, 1);

            gl.glLoadMatrixf(mMatrixValues, 0);
            gl.glDrawArrays(GL11.GL_TRIANGLE_FAN, OFFSET_FILL_CIRCLE, OFFSET_LAST - OFFSET_FILL_CIRCLE - conjugateCount);

            restoreTransform();
            mCountFillRect++;
        }
    }

    @Override
    public void translate(float x, float y, float z) {
        Matrix.translateM(mMatrixValues, 0, x, y, z);
    }

    // This is a faster version of translate(x, y, z) because
    // (1) we knows z = 0, (2) we inline the Matrix.translateM call,
    // (3) we unroll the loop
    @Override
    public void translate(float x, float y) {
        float[] m = mMatrixValues;
        m[12] += m[0] * x + m[4] * y;
        m[13] += m[1] * x + m[5] * y;
        m[14] += m[2] * x + m[6] * y;
        m[15] += m[3] * x + m[7] * y;
    }

    @Override
    public void scale(float sx, float sy, float sz) {
        Matrix.scaleM(mMatrixValues, 0, sx, sy, sz);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        if (angle == 0) return;
        float[] temp = mTempMatrix;
        Matrix.setRotateM(temp, 0, angle, x, y, z);
        Matrix.multiplyMM(temp, 16, mMatrixValues, 0, temp, 0);
        System.arraycopy(temp, 16, mMatrixValues, 0, 16);
    }

    @Override
    public void multiplyMatrix(float[] matrix, int offset) {
        float[] temp = mTempMatrix;
        Matrix.multiplyMM(temp, 0, mMatrixValues, 0, matrix, offset);
        System.arraycopy(temp, 0, mMatrixValues, 0, 16);
    }

    private void textureRect(float x, float y, float width, float height) {
        GL11 gl = mGL;

        saveTransform();
        translate(x, y);
        scale(width, height, 1);

        gl.glLoadMatrixf(mMatrixValues, 0);
        gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, OFFSET_FILL_RECT, 4);

        restoreTransform();
        mCountTextureRect++;
    }

    @Override
    public void drawMesh(BasicTexture tex, int x, int y, int xyBuffer,
                         int uvBuffer, int indexBuffer, int indexCount) {
        float alpha = mAlpha;
        if (!bindTexture(tex)) return;

        mGLState.setBlendEnabled(mBlendEnabled
                && (!tex.isOpaque() || alpha < OPAQUE_ALPHA));
        mGLState.setTextureAlpha(alpha);

        // Reset the texture matrix. We will set our own texture coordinates
        // below.
        setTextureCoords(0, 0, 1, 1);

        saveTransform();
        translate(x, y);

        mGL.glLoadMatrixf(mMatrixValues, 0);

        mGL.glBindBuffer(GL11.GL_ARRAY_BUFFER, xyBuffer);
        mGL.glVertexPointer(2, GL11.GL_FLOAT, 0, 0);

        mGL.glBindBuffer(GL11.GL_ARRAY_BUFFER, uvBuffer);
        mGL.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);

        mGL.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        mGL.glDrawElements(GL11.GL_TRIANGLE_STRIP,
                indexCount, GL11.GL_UNSIGNED_BYTE, 0);

        mGL.glBindBuffer(GL11.GL_ARRAY_BUFFER, mBoxCoords);
        mGL.glVertexPointer(2, GL11.GL_FLOAT, 0, 0);
        mGL.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);

        restoreTransform();
        mCountDrawMesh++;
    }

    // Transforms two points by the given matrix m. The result
    // {x1', y1', x2', y2'} are stored in mMapPointsBuffer and also returned.
    private float[] mapPoints(float[] m, int x1, int y1, int x2, int y2) {
        float[] r = mMapPointsBuffer;

        // Multiply m and (x1 y1 0 1) to produce (x3 y3 z3 w3). z3 is unused.
        float x3 = m[0] * x1 + m[4] * y1 + m[12];
        float y3 = m[1] * x1 + m[5] * y1 + m[13];
        float w3 = m[3] * x1 + m[7] * y1 + m[15];
        r[0] = x3 / w3;
        r[1] = y3 / w3;

        // Same for x2 y2.
        float x4 = m[0] * x2 + m[4] * y2 + m[12];
        float y4 = m[1] * x2 + m[5] * y2 + m[13];
        float w4 = m[3] * x2 + m[7] * y2 + m[15];
        r[2] = x4 / w4;
        r[3] = y4 / w4;

        return r;
    }

    private void drawBoundTexture(
            BasicTexture texture, int x, int y, int width, int height) {
        // Test whether it has been rotated or flipped, if so, glDrawTexiOES
        // won't work
        if (isMatrixRotatedOrFlipped(mMatrixValues)) {
            if (texture.hasBorder()) {
                setTextureCoords(
                        1.0f / texture.getTextureWidth(),
                        1.0f / texture.getTextureHeight(),
                        (texture.getWidth() - 1.0f) / texture.getTextureWidth(),
                        (texture.getHeight() - 1.0f) / texture.getTextureHeight());
            } else {
                setTextureCoords(0, 0,
                        (float) texture.getWidth() / texture.getTextureWidth(),
                        (float) texture.getHeight() / texture.getTextureHeight());
            }
            textureRect(x, y, width, height);
        } else {
            // draw the rect from bottom-left to top-right
            float[] points = mapPoints(
                    mMatrixValues, x, y + height, x + width, y);
            x = (int) (points[0] + 0.5f);
            y = (int) (points[1] + 0.5f);
            width = (int) (points[2] + 0.5f) - x;
            height = (int) (points[3] + 0.5f) - y;
            if (width > 0 && height > 0) {
                ((GL11Ext) mGL).glDrawTexiOES(x, y, 0, width, height);
                mCountTextureOES++;
            }
        }
    }

    @Override
    public void drawTexture(
            BasicTexture texture, int x, int y, int width, int height) {
        drawTexture(texture, x, y, width, height, mAlpha);
    }

    private void drawTexture(BasicTexture texture,
                             int x, int y, int width, int height, float alpha) {
        if (width <= 0 || height <= 0) return;

        mGLState.setBlendEnabled(mBlendEnabled
                && (!texture.isOpaque() || alpha < OPAQUE_ALPHA));
        if (!bindTexture(texture)) return;
        mGLState.setTextureAlpha(alpha);
        drawBoundTexture(texture, x, y, width, height);
    }

    @Override
    public void drawTexture(BasicTexture texture, RectF source, RectF target) {
        if (target.width() <= 0 || target.height() <= 0) return;

        // Copy the input to avoid changing it.
        mDrawTextureSourceRect.set(source);
        mDrawTextureTargetRect.set(target);
        source = mDrawTextureSourceRect;
        target = mDrawTextureTargetRect;

        mGLState.setBlendEnabled(mBlendEnabled
                && (!texture.isOpaque() || mAlpha < OPAQUE_ALPHA));
        if (!bindTexture(texture)) return;
        convertCoordinate(source, target, texture);
        setTextureCoords(source);
        mGLState.setTextureAlpha(mAlpha);
        textureRect(target.left, target.top, target.width(), target.height());
    }

    @Override
    public void drawTexture(BasicTexture texture, float[] mTextureTransform,
                            int x, int y, int w, int h) {
        mGLState.setBlendEnabled(mBlendEnabled
                && (!texture.isOpaque() || mAlpha < OPAQUE_ALPHA));
        if (!bindTexture(texture)) return;
        setTextureCoords(mTextureTransform);
        mGLState.setTextureAlpha(mAlpha);
        textureRect(x, y, w, h);
    }

    @Override
    public void drawMixed(BasicTexture from,
                          int toColor, float ratio, int x, int y, int w, int h) {
        drawMixed(from, toColor, ratio, x, y, w, h, mAlpha);
    }

    private boolean bindTexture(BasicTexture texture) {
        if (!texture.onBind(this)) return false;
        int target = texture.getTarget();
        mGLState.setTextureTarget(target);
        mGL.glBindTexture(target, texture.getId());
        return true;
    }

    private void setTextureColor(float r, float g, float b, float alpha) {
        float[] color = mTextureColor;
        color[0] = r;
        color[1] = g;
        color[2] = b;
        color[3] = alpha;
    }

    private void setMixedColor(int toColor, float ratio, float alpha) {
        //
        // The formula we want:
        //     alpha * ((1 - ratio) * from + ratio * to)
        //
        // The formula that GL supports is in the form of:
        //     combo * from + (1 - combo) * to * scale
        //
        // So, we have combo = alpha * (1 - ratio)
        //     and     scale = alpha * ratio / (1 - combo)
        //
        float combo = alpha * (1 - ratio);
        float scale = alpha * ratio / (1 - combo);

        // Specify the interpolation factor via the alpha component of
        // GL_TEXTURE_ENV_COLORs.
        // RGB component are get from toColor and will used as SRC1
        float colorScale = scale * (toColor >>> 24) / (0xff * 0xff);
        setTextureColor(((toColor >>> 16) & 0xff) * colorScale,
                ((toColor >>> 8) & 0xff) * colorScale,
                (toColor & 0xff) * colorScale, combo);
        GL11 gl = mGL;
        gl.glTexEnvfv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, mTextureColor, 0);

        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_INTERPOLATE);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_INTERPOLATE);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_SRC1_RGB, GL11.GL_CONSTANT);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_SRC1_ALPHA, GL11.GL_CONSTANT);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);

        // Wire up the interpolation factor for RGB.
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_SRC2_RGB, GL11.GL_CONSTANT);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_OPERAND2_RGB, GL11.GL_SRC_ALPHA);

        // Wire up the interpolation factor for alpha.
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_SRC2_ALPHA, GL11.GL_CONSTANT);
        gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_OPERAND2_ALPHA, GL11.GL_SRC_ALPHA);

    }

    @Override
    public void drawMixed(BasicTexture from, int toColor, float ratio,
                          RectF source, RectF target) {
        if (target.width() <= 0 || target.height() <= 0) return;

        if (ratio <= 0.01f) {
            drawTexture(from, source, target);
            return;
        } else if (ratio >= 1) {
            fillRect(target.left, target.top, target.width(), target.height(), toColor);
            return;
        }

        float alpha = mAlpha;

        // Copy the input to avoid changing it.
        mDrawTextureSourceRect.set(source);
        mDrawTextureTargetRect.set(target);
        source = mDrawTextureSourceRect;
        target = mDrawTextureTargetRect;

        mGLState.setBlendEnabled(mBlendEnabled && (!from.isOpaque()
                || Color.alpha(toColor) != 255 || alpha < OPAQUE_ALPHA));

        if (!bindTexture(from)) return;

        // Interpolate the RGB and alpha values between both textures.
        mGLState.setTexEnvMode(GL11.GL_COMBINE);
        setMixedColor(toColor, ratio, alpha);
        convertCoordinate(source, target, from);
        setTextureCoords(source);
        textureRect(target.left, target.top, target.width(), target.height());
        mGLState.setTexEnvMode(GL11.GL_REPLACE);
    }

    private void drawMixed(BasicTexture from, int toColor,
                           float ratio, int x, int y, int width, int height, float alpha) {
        // change from 0 to 0.01f to prevent getting divided by zero below
        if (ratio <= 0.01f) {
            drawTexture(from, x, y, width, height, alpha);
            return;
        } else if (ratio >= 1) {
            fillRect(x, y, width, height, toColor);
            return;
        }

        mGLState.setBlendEnabled(mBlendEnabled && (!from.isOpaque()
                || Color.alpha(toColor) != 255 || alpha < OPAQUE_ALPHA));

        final GL11 gl = mGL;
        if (!bindTexture(from)) return;

        // Interpolate the RGB and alpha values between both textures.
        mGLState.setTexEnvMode(GL11.GL_COMBINE);
        setMixedColor(toColor, ratio, alpha);

        drawBoundTexture(from, x, y, width, height);
        mGLState.setTexEnvMode(GL11.GL_REPLACE);
    }

    @Override
    public void clearBuffer(float[] argb) {
        if (argb != null && argb.length == 4) {
            mGL.glClearColor(argb[1], argb[2], argb[3], argb[0]);
        } else {
            mGL.glClearColor(0, 0, 0, 1);
        }
        mGL.glClear(GL10.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void clearBuffer() {
        clearBuffer(null);
    }

    private void setTextureCoords(RectF source) {
        setTextureCoords(source.left, source.top, source.right, source.bottom);
    }

    private void setTextureCoords(float left, float top,
                                  float right, float bottom) {
        mGL.glMatrixMode(GL11.GL_TEXTURE);
        mTextureMatrixValues[0] = right - left;
        mTextureMatrixValues[5] = bottom - top;
        mTextureMatrixValues[10] = 1;
        mTextureMatrixValues[12] = left;
        mTextureMatrixValues[13] = top;
        mTextureMatrixValues[15] = 1;
        mGL.glLoadMatrixf(mTextureMatrixValues, 0);
        mGL.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void setTextureCoords(float[] mTextureTransform) {
        mGL.glMatrixMode(GL11.GL_TEXTURE);
        mGL.glLoadMatrixf(mTextureTransform, 0);
        mGL.glMatrixMode(GL11.GL_MODELVIEW);
    }

    // unloadTexture and deleteBuffer can be called from the finalizer thread,
    // so we synchronized on the mUnboundTextures object.
    @Override
    public boolean unloadTexture(BasicTexture t) {
        synchronized (mUnboundTextures) {
            if (!t.isLoaded()) return false;
            mUnboundTextures.add(t.mId);
            return true;
        }
    }

    @Override
    public void deleteBuffer(int bufferId) {
        synchronized (mUnboundTextures) {
            mDeleteBuffers.add(bufferId);
        }
    }

    @Override
    public void deleteRecycledResources() {
        synchronized (mUnboundTextures) {
            IntList ids = mUnboundTextures;
            if (ids.size() > 0) {
                mGLId.glDeleteTextures(mGL, ids.size(), ids.getInternalArray(), 0);
                ids.clear();
            }

            ids = mDeleteBuffers;
            if (ids.size() > 0) {
                mGLId.glDeleteBuffers(mGL, ids.size(), ids.getInternalArray(), 0);
                ids.clear();
            }
        }
    }

    @Override
    public void save() {
        save(SAVE_FLAG_ALL);
    }

    @Override
    public void save(int saveFlags) {
        ConfigState config = obtainRestoreConfig();

        if ((saveFlags & SAVE_FLAG_ALPHA) != 0) {
            config.mAlpha = mAlpha;
        } else {
            config.mAlpha = -1;
        }

        if ((saveFlags & SAVE_FLAG_MATRIX) != 0) {
            System.arraycopy(mMatrixValues, 0, config.mMatrix, 0, 16);
        } else {
            config.mMatrix[0] = Float.NEGATIVE_INFINITY;
        }

        mRestoreStack.add(config);
    }

    @Override
    public void restore() {
        if (mRestoreStack.isEmpty()) throw new IllegalStateException();
        ConfigState config = mRestoreStack.remove(mRestoreStack.size() - 1);
        config.restore(this);
        freeRestoreConfig(config);
    }

    private void freeRestoreConfig(ConfigState action) {
        action.mNextFree = mRecycledRestoreAction;
        mRecycledRestoreAction = action;
    }

    private ConfigState obtainRestoreConfig() {
        if (mRecycledRestoreAction != null) {
            ConfigState result = mRecycledRestoreAction;
            mRecycledRestoreAction = result.mNextFree;
            return result;
        }
        return new ConfigState();
    }

    @Override
    public void dumpStatisticsAndClear() {
        String line = String.format(
                "MESH:%d, TEX_OES:%d, TEX_RECT:%d, FILL_RECT:%d, LINE:%d",
                mCountDrawMesh, mCountTextureRect, mCountTextureOES,
                mCountFillRect, mCountDrawLine);
        mCountDrawMesh = 0;
        mCountTextureRect = 0;
        mCountTextureOES = 0;
        mCountFillRect = 0;
        mCountDrawLine = 0;
        Log.d(TAG, line);
    }

    private void saveTransform() {
        System.arraycopy(mMatrixValues, 0, mTempMatrix, 0, 16);
    }

    private void restoreTransform() {
        System.arraycopy(mTempMatrix, 0, mMatrixValues, 0, 16);
    }

    private void setRenderTarget(RawTexture texture) {
        GL11ExtensionPack gl11ep = (GL11ExtensionPack) mGL;

        if (mTargetTexture == null && texture != null) {
            mGLId.glGenBuffers(1, mFrameBuffer, 0);
            gl11ep.glBindFramebufferOES(
                    GL11ExtensionPack.GL_FRAMEBUFFER_OES, mFrameBuffer[0]);
        }
        if (mTargetTexture != null && texture == null) {
            gl11ep.glBindFramebufferOES(GL11ExtensionPack.GL_FRAMEBUFFER_OES, 0);
            gl11ep.glDeleteFramebuffersOES(1, mFrameBuffer, 0);
        }

        mTargetTexture = texture;
        if (texture == null) {
            setSize(mScreenWidth, mScreenHeight);
        } else {
            setSize(texture.getWidth(), texture.getHeight());

            if (!texture.isLoaded()) texture.prepare(this);

            gl11ep.glFramebufferTexture2DOES(
                    GL11ExtensionPack.GL_FRAMEBUFFER_OES,
                    GL11ExtensionPack.GL_COLOR_ATTACHMENT0_OES,
                    GL11.GL_TEXTURE_2D, texture.getId(), 0);

            checkFramebufferStatus(gl11ep);
        }
    }

    @Override
    public void endRenderTarget() {
        RawTexture texture = mTargetStack.remove(mTargetStack.size() - 1);
        setRenderTarget(texture);
        restore(); // restore matrix and alpha
    }

    @Override
    public void beginRenderTarget(RawTexture texture) {
        save(); // save matrix and alpha
        mTargetStack.add(mTargetTexture);
        setRenderTarget(texture);
    }

    @Override
    public void setTextureParameters(BasicTexture texture) {
        int width = texture.getWidth();
        int height = texture.getHeight();
        // Define a vertically flipped crop rectangle for OES_draw_texture.
        // The four values in sCropRect are: left, bottom, width, and
        // height. Negative value of width or height means flip.
        sCropRect[0] = 0;
        sCropRect[1] = height;
        sCropRect[2] = width;
        sCropRect[3] = -height;

        // Set texture parameters.
        int target = texture.getTarget();
        mGL.glBindTexture(target, texture.getId());
        mGL.glTexParameterfv(target, GL11Ext.GL_TEXTURE_CROP_RECT_OES, sCropRect, 0);
        mGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
        mGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
        mGL.glTexParameterf(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        mGL.glTexParameterf(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    @Override
    public void initializeTextureSize(BasicTexture texture, int format, int type) {
        int target = texture.getTarget();
        mGL.glBindTexture(target, texture.getId());
        int width = texture.getTextureWidth();
        int height = texture.getTextureHeight();
        mGL.glTexImage2D(target, 0, format, width, height, 0, format, type, null);
    }

    @Override
    public void initializeTexture(BasicTexture texture, Bitmap bitmap) {
        int target = texture.getTarget();
        mGL.glBindTexture(target, texture.getId());
        GLUtils.texImage2D(target, 0, bitmap, 0);
    }

    @Override
    public void texSubImage2D(BasicTexture texture, int xOffset, int yOffset, Bitmap bitmap,
                              int format, int type) {
        int target = texture.getTarget();
        mGL.glBindTexture(target, texture.getId());
        GLUtils.texSubImage2D(target, 0, xOffset, yOffset, bitmap, format, type);
    }

    @Override
    public int uploadBuffer(FloatBuffer buf) {
        return uploadBuffer(buf, Float.SIZE / Byte.SIZE);
    }

    @Override
    public int uploadBuffer(ByteBuffer buf) {
        return uploadBuffer(buf, 1);
    }

    private int uploadBuffer(Buffer buf, int elementSize) {
        int[] bufferIds = new int[1];
        mGLId.glGenBuffers(bufferIds.length, bufferIds, 0);
        int bufferId = bufferIds[0];
        mGL.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferId);
        mGL.glBufferData(GL11.GL_ARRAY_BUFFER, buf.capacity() * elementSize, buf,
                GL11.GL_STATIC_DRAW);
        return bufferId;
    }

    @Override
    public void recoverFromLightCycle() {
        // This is only required for GLES20
    }

    @Override
    public void getBounds(Rect bounds, int x, int y, int width, int height) {
        // This is only required for GLES20
    }

    @Override
    public GLId getGLId() {
        return mGLId;
    }

    private static class GLState {

        private final GL11 mGL;
        private final boolean mLineSmooth = false;
        private int mTexEnvMode = GL11.GL_REPLACE;
        private float mTextureAlpha = 1.0f;
        private int mTextureTarget = GL11.GL_TEXTURE_2D;
        private boolean mBlendEnabled = true;
        private float mLineWidth = 1.0f;

        public GLState(GL11 gl) {
            mGL = gl;

            // Disable unused state
            gl.glDisable(GL11.GL_LIGHTING);

            // Enable used features
            gl.glEnable(GL11.GL_DITHER);

            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glEnable(GL11.GL_TEXTURE_2D);

            gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                    GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

            // Set the background color
            gl.glClearColor(0f, 0f, 0f, 0f);

            gl.glEnable(GL11.GL_BLEND);
            gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // We use 565 or 8888 format, so set the alignment to 2 bytes/pixel.
            gl.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 2);
        }

        public void setTexEnvMode(int mode) {
            if (mTexEnvMode == mode) return;
            mTexEnvMode = mode;
            mGL.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, mode);
        }

        public void setLineWidth(float width) {
            if (mLineWidth == width) return;
            mLineWidth = width;
            mGL.glLineWidth(width);
        }

        public void setTextureAlpha(float alpha) {
            if (mTextureAlpha == alpha) return;
            mTextureAlpha = alpha;
            if (alpha >= OPAQUE_ALPHA) {
                // The alpha is need for those texture without alpha channel
                mGL.glColor4f(1, 1, 1, 1);
                setTexEnvMode(GL11.GL_REPLACE);
            } else {
                mGL.glColor4f(alpha, alpha, alpha, alpha);
                setTexEnvMode(GL11.GL_MODULATE);
            }
        }

        public void setColorMode(int color, float alpha) {
            setBlendEnabled(Color.alpha(color) != 255 || alpha < OPAQUE_ALPHA);

            // Set mTextureAlpha to an invalid value, so that it will reset
            // again in setTextureAlpha(float) later.
            mTextureAlpha = -1.0f;

            setTextureTarget(0);

            float prealpha = (color >>> 24) * alpha * 65535f / 255f / 255f;
            mGL.glColor4x(
                    Math.round(((color >> 16) & 0xFF) * prealpha),
                    Math.round(((color >> 8) & 0xFF) * prealpha),
                    Math.round((color & 0xFF) * prealpha),
                    Math.round(255 * prealpha));
        }

        // target is a value like GL_TEXTURE_2D. If target = 0, texturing is disabled.
        public void setTextureTarget(int target) {
            if (mTextureTarget == target) return;
            if (mTextureTarget != 0) {
                mGL.glDisable(mTextureTarget);
            }
            mTextureTarget = target;
            if (mTextureTarget != 0) {
                mGL.glEnable(mTextureTarget);
            }
        }

        public void setBlendEnabled(boolean enabled) {
            if (mBlendEnabled == enabled) return;
            mBlendEnabled = enabled;
            if (enabled) {
                mGL.glEnable(GL11.GL_BLEND);
            } else {
                mGL.glDisable(GL11.GL_BLEND);
            }
        }
    }

    private static class ConfigState {
        float mAlpha;
        float[] mMatrix = new float[16];
        ConfigState mNextFree;

        public void restore(GLES11Canvas canvas) {
            if (mAlpha >= 0) canvas.setAlpha(mAlpha);
            if (mMatrix[0] != Float.NEGATIVE_INFINITY) {
                System.arraycopy(mMatrix, 0, canvas.mMatrixValues, 0, 16);
            }
        }
    }
}
