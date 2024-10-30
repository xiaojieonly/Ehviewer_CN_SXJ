/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.glview.widget;

import android.opengl.Matrix;

import androidx.annotation.IntDef;

import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.glview.view.GLView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class GLEdgeView extends GLView {

    @IntDef({TOP, LEFT, BOTTOM, RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Direction {}

    public static final int TOP = 0;
    public static final int LEFT = 1;
    public static final int BOTTOM = 2;
    public static final int RIGHT = 3;

    // Each edge effect has a transform matrix, and each matrix has 16 elements.
    // We put all the elements in one array. These constants specify the
    // starting index of each matrix.
    private static final int TOP_M = TOP * 16;
    private static final int LEFT_M = LEFT * 16;
    private static final int BOTTOM_M = BOTTOM * 16;
    private static final int RIGHT_M = RIGHT * 16;

    private final GLEdgeEffect[] mEffect = new GLEdgeEffect[4];
    private final float[] mMatrix = new float[4 * 16];

    public GLEdgeView(int color) {
        for (int i = 0; i < 4; i++) {
            mEffect[i] = new GLEdgeEffect(color);
        }
    }

    public void setColor(int color) {
        for (int i = 0; i < 4; i++) {
            mEffect[i].setColor(color);
        }
    }

    @Override
    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
        if (!changeSize) return;

        int w = right - left;
        int h = bottom - top;
        for (int i = 0; i < 4; i++) {
            if ((i & 1) == 0) {  // top or bottom
                mEffect[i].setSize(w, h);
            } else {  // left or right
                mEffect[i].setSize(h, w);
            }
        }

        // Set up transforms for the four edges. Without transforms an
        // EdgeEffect draws the TOP edge from (0, 0) to (w, Y * h) where Y
        // is some factor < 1. For other edges we need to move, rotate, and
        // flip the effects into proper places.
        Matrix.setIdentityM(mMatrix, TOP_M);
        Matrix.setIdentityM(mMatrix, LEFT_M);
        Matrix.setIdentityM(mMatrix, BOTTOM_M);
        Matrix.setIdentityM(mMatrix, RIGHT_M);

        Matrix.rotateM(mMatrix, LEFT_M, 90, 0, 0, 1);
        Matrix.scaleM(mMatrix, LEFT_M, 1, -1, 1);

        Matrix.translateM(mMatrix, BOTTOM_M, 0, h, 0);
        Matrix.scaleM(mMatrix, BOTTOM_M, 1, -1, 1);

        Matrix.translateM(mMatrix, RIGHT_M, w, 0, 0);
        Matrix.rotateM(mMatrix, RIGHT_M, 90, 0, 0, 1);
    }

    @Override
    public void render(GLCanvas canvas) {
        boolean more = false;
        for (int i = 0; i < 4; i++) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.multiplyMatrix(mMatrix, i * 16);
            more |= mEffect[i].draw(canvas);
            canvas.restore();
        }
        if (more) {
            invalidate();
        }
    }

    // Called when the content is pulled away from the edge.
    // offset is in pixels. direction is one of {TOP, LEFT, BOTTOM, RIGHT}.
    public void onPull(float offset, @Direction int direction) {
        int fullLength = ((direction & 1) == 0) ? getWidth() : getHeight();
        mEffect[direction].onPull(offset / fullLength);
        if (!mEffect[direction].isFinished()) {
            invalidate();
        }
    }

    public void onPull(float offset, float position, @Direction int direction) {
        int fullLength = ((direction & 1) == 0) ? getHeight() : getWidth();
        int fullPosition = ((direction & 1) == 0) ? getWidth() : getHeight();
        mEffect[direction].onPull(offset / fullLength, position / fullPosition);
        if (!mEffect[direction].isFinished()) {
            invalidate();
        }
    }

    // Call when the object is released after being pulled.
    public void onRelease() {
        boolean more = false;
        for (int i = 0; i < 4; i++) {
            mEffect[i].onRelease();
            more |= !mEffect[i].isFinished();
        }
        if (more) {
            invalidate();
        }
    }

    public void onRelease(@Direction int direction) {
        GLEdgeEffect edgeEffect = mEffect[direction];
        edgeEffect.onRelease();
        if (!edgeEffect.isFinished()) {
            invalidate();
        }
    }

    // Call when the effect absorbs an impact at the given velocity.
    // Used when a fling reaches the scroll boundary. velocity is in pixels
    // per second. direction is one of {TOP, LEFT, BOTTOM, RIGHT}.
    public void onAbsorb(int velocity, int direction) {
        mEffect[direction].onAbsorb(velocity);
        if (!mEffect[direction].isFinished()) {
            invalidate();
        }
    }

    public boolean isFinished(int direction) {
        return mEffect[direction].isFinished();
    }
}
