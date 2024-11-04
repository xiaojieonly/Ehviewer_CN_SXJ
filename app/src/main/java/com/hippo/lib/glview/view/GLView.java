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

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;

import androidx.annotation.IntDef;

import com.hippo.lib.glview.anim.CanvasAnimation;
import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.glview.glrenderer.GLPaint;
import com.hippo.lib.yorozuya.AssertUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

// GLView is a UI component. It can render to a GLCanvas and accept touch
// events. A GLView may have zero or more child GLView and they form a tree
// structure. The rendering and event handling will pass through the tree
// structure.
//
// A GLView tree should be attached to a GLRoot before event dispatching and
// rendering happens. GLView asks GLRoot to re-render or re-layout the
// GLView hierarchy using requestRender() and requestLayoutContentPane().
//
// The render() method is called in a separate thread. Before calling
// dispatchTouchEvent() and layout(), GLRoot acquires a lock to avoid the
// rendering thread running at the same time. If there are other entry points
// from main thread (like a Handler) in your GLView, you need to call
// lockRendering() if the rendering thread should not run at the same time.
//
public class GLView implements TouchOwner {
    private static final String TAG = "GLView";

    private static final boolean DEBUG_DRAW_BOUNDS = false;

    @IntDef({VISIBLE, INVISIBLE, GONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Visibility {}

    public static final int VISIBLE =                   0b00000000;
    public static final int INVISIBLE =                 0b00000001;
    public static final int GONE =                      0b00000010;
    public static final int VISIBILITY_INVALID =        0b00000011;

    private static final int FLAG_INVISIBLE =           0b00000011;
    private static final int FLAG_SET_MEASURED_SIZE =   0b00000100;
    private static final int FLAG_LAYOUT_REQUESTED =    0b00001000;

    /**
     * Used to mark a GlView that has no ID.
     */
    public static final int NO_ID = -1;

    public interface OnClickListener {
        boolean onClick(GLView v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(GLView v);
    }

    protected final static GLPaint mDrawBoundsPaint;

    static {
        mDrawBoundsPaint = new GLPaint();
        mDrawBoundsPaint.setColor(Color.RED);
        mDrawBoundsPaint.setLineWidth(2);
    }

    /**
     * Position in parent, just like left, top, right, bottom in {@link android.view.View}
     */
    protected final Rect mBounds = new Rect();
    /**
     * Position in root
     */
    private final int[] mPositionInRoot = new int[2];
    protected final Rect mPaddings = new Rect();
    private final Rect mTempRect = new Rect();

    private GLRoot mRoot;
    protected GLView mParent;
    private ArrayList<GLView> mComponents;
    private GLView mMotionTarget;

    private CanvasAnimation mAnimation;

    private int mViewFlags = 0;

    protected int mMeasuredWidth = 0;
    protected int mMeasuredHeight = 0;

    private int mLastWidthSpec = -1;
    private int mLastHeightSpec = -1;

    protected int mScrollY = 0;
    protected int mScrollX = 0;
    protected int mScrollHeight = 0;
    protected int mScrollWidth = 0;

    /**
     * The GlView's identifier.
     *
     * @see #setId(int)
     * @see #getId()
     */
    private int mID = NO_ID;

    /**
     * The minimum height of the view. We'll try our best to have the height
     * of this view to at least this amount.
     */
    private int mMinHeight;

    /**
     * The minimum width of the view. We'll try our best to have the width
     * of this view to at least this amount.
     */
    private int mMinWidth;

    private LayoutParams mLayoutParams;

    private int mBackgroundColor = Color.TRANSPARENT;

    private TouchHelper mTouchHelper;

    private float mHotspotX;
    private float mHotspotY;

    private boolean mPressed;

    private OnClickListener mOnClickListener;
    private OnLongClickListener mOnLongClickListener;

    public void startAnimation(CanvasAnimation animation, boolean atOnce) {
        GLRoot root = getGLRoot();
        if (root == null) throw new IllegalStateException();
        mAnimation = animation;
        if (mAnimation != null) {
            mAnimation.start();
            if (atOnce) {
                mAnimation.setStartTime(AnimationTime.get());
            } else {
                root.registerLaunchedAnimation(mAnimation);
            }
        }
        invalidate();
    }

    /**
     * Set the enabled state of this view.
     *
     * @param visibility One of {@link #VISIBLE}, {@link #INVISIBLE}, or {@link #GONE}.
     */
    public void setVisibility(@Visibility int visibility) {
        @Visibility
        int oldVisibility = getVisibility();
        if (visibility == oldVisibility) {
            return;
        }

        mViewFlags &= ~FLAG_INVISIBLE;
        mViewFlags |= visibility;

        onVisibilityChanged(this, visibility);

        if (oldVisibility == GLView.GONE || visibility == GLView.GONE) {
            requestLayout();
        } else {
            invalidate();
        }
    }

    /**
     * Returns the visibility status for this view.
     *
     * @return One of {@link #VISIBLE}, {@link #INVISIBLE}, or {@link #GONE}.
     */
    @Visibility
    @SuppressWarnings("WrongConstant")
    public int getVisibility() {
        int visibility = mViewFlags & FLAG_INVISIBLE;

        if (visibility == VISIBILITY_INVALID) {
            visibility = VISIBLE;
            mViewFlags &= ~FLAG_INVISIBLE;
            mViewFlags |= visibility;
        }

        return visibility;
    }

    // This should only be called on the content pane (the topmost GLView).
    void attachToRoot(GLRoot root) {
        AssertUtils.assertTrue(mParent == null && mRoot == null);
        onAttachToRoot(root);
    }

    // This should only be called on the content pane (the topmost GLView).
    void detachFromRoot() {
        AssertUtils.assertTrue(mParent == null && mRoot != null);
        onDetachFromRoot();
    }

    // This should only be called on the content pane (the topmost GLView).
    void pause() {
        onPause();
    }

    // This should only be called on the content pane (the topmost GLView).
    void resume() {
        onResume();
    }

    public boolean isAttachedToRoot() {
        return mRoot != null;
    }

    // Returns the number of children of the GLView.
    public int getComponentCount() {
        return mComponents == null ? 0 : mComponents.size();
    }

    // Returns the children for the given index.
    public GLView getComponent(int index) {
        if (mComponents == null) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mComponents.get(index);
    }

    // Adds a child to this GLView.
    public void addComponent(GLView component) {
        addComponent(component, -1, null);
    }

    public void addComponent(GLView component, int index) {
        addComponent(component, index, null);
    }

    public void addComponent(GLView component, LayoutParams params) {
        addComponent(component, -1, params);
    }

    public void addComponent(GLView component, int index, LayoutParams params) {
        // Make component is not null
        if (component == null) {
            throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        }
        // Make sure the component doesn't have a parent currently.
        if (component.mParent != null) {
            throw new IllegalStateException(
                    "Component " + this + " being added, but it already has a parent");
        }

        // Ensure index is valid
        if (index < 0) {
            index = getComponentCount();
        }

        // Ensure params is valid
        if (params == null) {
            params = generateDefaultLayoutParams();
        } else if (!checkLayoutParams(params)) {
            params = generateLayoutParams(params);
        }

        // Build parent-child links
        if (mComponents == null) {
            mComponents = new ArrayList<>();
        }

        mComponents.add(index, component);
        component.mParent = this;
        component.setLayoutParams(params);

        // If this is added after we have a root, tell the component.
        if (mRoot != null) {
            component.onAttachToRoot(mRoot);
        }
    }

    // Removes a child from this GLView.
    public boolean removeComponent(GLView component) {
        if (mComponents == null) return false;
        if (mComponents.remove(component)) {
            removeOneComponent(component);
            return true;
        }
        return false;
    }

    public boolean removeComponentAt(int index) {
        if (mComponents == null) {
            return false;
        }
        if (index >= 0 && index < getComponentCount()) {
            GLView component = mComponents.remove(index);
            removeOneComponent(component);
            return true;
        } else {
            return false;
        }
    }

    // Removes all children of this GLView.
    public void removeAllComponents() {
        for (int i = 0, n = mComponents.size(); i < n; ++i) {
            removeOneComponent(mComponents.get(i));
        }
        mComponents.clear();
    }

    private void removeOneComponent(GLView component) {
        if (mMotionTarget == component) {
            long now = SystemClock.uptimeMillis();
            MotionEvent cancelEvent = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            dispatchTouchEvent(cancelEvent);
            cancelEvent.recycle();
        }
        component.onDetachFromRoot();
        component.mParent = null;
    }

    /**
     * Sets the identifier for this GlView. The identifier does not have to be
     * unique in this GlView's hierarchy. The identifier should be a positive
     * number.
     *
     * @param id a number used to identify the view
     */
    public void setId(int id) {
        mID = id;
    }

    /**
     * Returns this GlView's identifier.
     *
     * @return a positive integer used to identify the view or {@link #NO_ID}
     *         if the view has no ID
     */
    public int getId() {
        return mID;
    }

    public Rect bounds() {
        return mBounds;
    }

    @Override
    public void setHotspot(float x, float y) {
        mHotspotX = x;
        mHotspotY = y;
    }

    public float getHotspotX() {
        return mHotspotX;
    }

    public float getHotspotY() {
        return mHotspotY;
    }

    @Override
    public boolean isEnabled() {
        return true; // TODO
    }

    @Override
    public boolean isPressed() {
        return mPressed;
    }

    @Override
    public void setPressed(boolean pressed) {
        mPressed = pressed;
    }

    @Override
    public boolean isClickable() {
        return mOnClickListener != null;
    }

    @Override
    public boolean isLongClickable() {
        return mOnLongClickListener != null;
    }

    @Override
    public boolean performClick() {
        if (mOnClickListener != null) {
            return mOnClickListener.onClick(this);
        } else {
            return false;
        }
    }

    @Override
    public boolean performLongClick() {
        if (mOnLongClickListener != null) {
            return mOnLongClickListener.onLongClick(this);
        } else {
            return false;
        }
    }

    public void setOnClickListener(OnClickListener listener) {
        mOnClickListener = listener;
    }

    public void setOnLongClickListener(OnLongClickListener listener) {
        mOnLongClickListener = listener;
    }

    @Override
    public int getWidth() {
        return mBounds.right - mBounds.left;
    }

    @Override
    public int getHeight() {
        return mBounds.bottom - mBounds.top;
    }

    /**
     * Offset this GLView's vertical location by the specified number of pixels.
     *
     * @param offset the number of pixels to offset the view by
     */
    public void offsetTopAndBottom(int offset) {
        if (offset != 0){
            mBounds.offset(0, offset);
            dispatchNotifyPositionInRoot();
            invalidate();
        }
    }

    /**
     * Offset this GLView's horizontal location by the specified amount of pixels.
     *
     * @param offset the number of pixels to offset the view by
     */
    public void offsetLeftAndRight(int offset) {
        if (offset != 0){
            mBounds.offset(offset, 0);
            dispatchNotifyPositionInRoot();
            invalidate();
        }
    }

    public GLRoot getGLRoot() {
        return mRoot;
    }

    // Request re-rendering of the view hierarchy.
    // This is used for animation or when the contents changed.
    public void invalidate() {
        GLRoot root = getGLRoot();
        if (root != null) root.requestRender();
    }

    // Request re-layout of the view hierarchy.
    public void requestLayout() {
        mViewFlags |= FLAG_LAYOUT_REQUESTED;
        mLastWidthSpec = -1;
        mLastHeightSpec = -1;
        if (mParent != null) {
            mParent.requestLayout();
        } else {
            // Is this a content pane ?
            GLRoot root = getGLRoot();
            if (root != null) root.requestLayoutContentPane();
        }
    }

    public void render(GLCanvas canvas) {
        // render background color
        if (Color.alpha(mBackgroundColor) != 0) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), mBackgroundColor);
        }

        // render content
        onRender(canvas);

        // render child
        canvas.save();
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            renderChild(canvas, getComponent(i));
        }
        canvas.restore();

        // render bounds
        if (DEBUG_DRAW_BOUNDS) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mDrawBoundsPaint);
        }
    }

    public void onRender(GLCanvas canvas) {
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
    }

    protected void renderChild(GLCanvas canvas, GLView component) {
        if (component.getVisibility() != GLView.VISIBLE) {
            return;
        }

        getValidRect(mTempRect);
        if (mTempRect.isEmpty()) {
            return;
        }

        int xOffset = component.mBounds.left - mScrollX;
        int yOffset = component.mBounds.top - mScrollY;

        canvas.translate(xOffset, yOffset);

        CanvasAnimation anim = component.mAnimation;
        if (anim != null) {
            canvas.save(anim.getCanvasSaveFlags());
            if (anim.calculate(AnimationTime.get())) {
                invalidate();
            } else {
                component.mAnimation = null;
            }
            anim.apply(canvas);
        }
        component.render(canvas);
        if (anim != null) canvas.restore();
        canvas.translate(-xOffset, -yOffset);
    }

    protected boolean onTouch(MotionEvent event) {
        if (mTouchHelper == null) {
            mTouchHelper = new TouchHelper(this);
        }
        return mTouchHelper.onTouch(event);
    }

    protected boolean dispatchTouchEvent(MotionEvent event,
            int x, int y, GLView component, boolean checkBounds) {
        Rect rect = component.mBounds;
        int left = rect.left;
        int top = rect.top;
        if (!checkBounds || rect.contains(x, y)) {
            event.offsetLocation(-left, -top);
            if (component.dispatchTouchEvent(event)) {
                event.offsetLocation(left, top);
                return true;
            }
            event.offsetLocation(left, top);
        }
        return false;
    }

    protected boolean dispatchTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        int action = event.getAction();
        if (mMotionTarget != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                dispatchTouchEvent(cancel, x, y, mMotionTarget, false);
                mMotionTarget = null;
            } else {
                dispatchTouchEvent(event, x, y, mMotionTarget, false);
                if (action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_UP) {
                    mMotionTarget = null;
                }
                return true;
            }
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // in the reverse rendering order
            for (int i = getComponentCount() - 1; i >= 0; --i) {
                GLView component = getComponent(i);
                if (component.getVisibility() != GLView.VISIBLE) continue;
                if (dispatchTouchEvent(event, x, y, component, true)) {
                    mMotionTarget = component;
                    return true;
                }
            }
        }
        return onTouch(event);
    }

    /**
     * Called by {@link GLRootView#dispatchSaveInstanceState(SparseArray)} to
     * store this GlView hierarchy's frozen state into the given container.
     *
     * @param container The SparseArray in which to save the view's state.
     */
    public void saveHierarchyState(SparseArray<Parcelable> container) {
        dispatchSaveInstanceState(container);
    }

    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        if (mID != NO_ID) {
            Parcelable state = onSaveInstanceState();
            if (state != null) {
                container.put(mID, state);
            }
        }
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).dispatchSaveInstanceState(container);
        }
    }

    protected Parcelable onSaveInstanceState() {
        return null;
    }

    /**
     * Called by {@link GLRootView#dispatchRestoreInstanceState(SparseArray)} to
     * restore this view hierarchy's frozen state from the given container.
     *
     * @param container The SparseArray which holds previously frozen states.
     */
    public void restoreHierarchyState(SparseArray<Parcelable> container) {
        dispatchRestoreInstanceState(container);
    }

    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        if (mID != NO_ID) {
            Parcelable state = container.get(mID);
            if (state != null) {
                onRestoreInstanceState(state);
            }
        }
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).dispatchRestoreInstanceState(container);
        }
    }

    protected void onRestoreInstanceState(Parcelable state) {
        if (state != null) {
            throw new IllegalArgumentException("Please override onRestoreInstanceState");
        }
    }

    public void setPaddings(int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
        mPaddings.set(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    public void setPaddingLeft(int paddingLeft) {
        mPaddings.left = paddingLeft;
    }

    public void setPaddingTop(int paddingTop) {
        mPaddings.top = paddingTop;
    }

    public void setPaddingRight(int paddingRight) {
        mPaddings.right = paddingRight;
    }

    public void setPaddingBottom(int paddingBottom) {
        mPaddings.bottom = paddingBottom;
    }

    public Rect getPaddings() {
        return mPaddings;
    }

    // There is a little different between GLView.layout() and View.layout().
    // onMeasure() is not called in GLView.layout().
    // For the content view of GLRootView, GLView.measure() is called before GLView.layout().
    // For component, GLView.measure() in called in parent's GLView.onMeasure().
    // So it is OK.
    public void layout(int left, int top, int right, int bottom) {
        boolean sizeChanged = setBounds(left, top, right, bottom);
        final boolean forceLayout = (mViewFlags & FLAG_LAYOUT_REQUESTED) == FLAG_LAYOUT_REQUESTED;
        if (sizeChanged || forceLayout) {
            notifyPositionInRoot();
            onLayout(sizeChanged, left, top, right, bottom);
        } else {
            dispatchNotifyPositionInRoot();
        }
        mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
    }

    public void dispatchNotifyPositionInRoot() {
        if (notifyPositionInRoot()) {
            for (int i = 0, size = getComponentCount(); i < size; i++) {
                getComponent(i).dispatchNotifyPositionInRoot();
            }
        }
    }

    public boolean notifyPositionInRoot() {
        // Update position in root
        int[] position = mPositionInRoot;
        int oldX = position[0];
        int oldY = position[1];
        if (mParent == null) {
            position[0] = 0;
            position[1] = 0;
        } else {
            mParent.getPositionInRoot(position);
            // Apply offset in parent
            position[0] += mBounds.left;
            position[1] += mBounds.top;
        }
        int x = position[0];
        int y = position[1];
        if (x != oldX || y != oldY) {
            onPositionInRootChanged(x, y, oldX, oldY);
            return true;
        } else {
            return false;
        }
    }

    private boolean setBounds(int left, int top, int right, int bottom) {
        Rect bounds = mBounds;
        int oldW = bounds.right - bounds.left;
        int oldH = bounds.bottom - bounds.top;
        int newW = right - left;
        int newH = bottom - top;

        boolean sizeChanged = oldW != newW || oldH != newH;
        bounds.set(left, top, right, bottom);

        if (sizeChanged) {
            onSizeChanged(newW, newH, oldW, oldH);
        }

        return sizeChanged;
    }

    protected void onSizeChanged(int newW, int newH, int oldW, int oldH) {
    }

    protected void onPositionInRootChanged(int x, int y, int oldX, int oldY) {
    }

    public void getPositionInRoot(int[] position) {
        AssertUtils.assertEquals("position should be 2 length int array", position.length, 2);
        position[0] = mPositionInRoot[0];
        position[1] = mPositionInRoot[1];
    }

    /**
     * Get the rect of the view clipped by root rect
     * @param rect the result
     */
    public void getValidRect(Rect rect) {
        GLRoot root = mRoot;
        if (root == null) {
            rect.setEmpty();
            return;
        }

        int x = mPositionInRoot[0];
        int y = mPositionInRoot[1];
        rect.set(x, y, x + getWidth(), y + getHeight());
        if (!rect.intersect(0, 0, root.getWidth(), root.getHeight())) {
            rect.setEmpty();
            return;
        }

        rect.offset(-x, -y);
    }

    public void measure(int widthSpec, int heightSpec) {
        final boolean forceLayout = (mViewFlags & FLAG_LAYOUT_REQUESTED) == FLAG_LAYOUT_REQUESTED;
        final boolean isExactly = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY &&
                MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY;
        final boolean matchingSize = isExactly &&
                getMeasuredWidth() == MeasureSpec.getSize(widthSpec) &&
                getMeasuredHeight() == MeasureSpec.getSize(heightSpec);
        if (forceLayout || !matchingSize &&
                (widthSpec != mLastWidthSpec ||
                        heightSpec != mLastHeightSpec)) {
            mLastWidthSpec = widthSpec;
            mLastHeightSpec = heightSpec;

            mViewFlags &= ~FLAG_SET_MEASURED_SIZE;
            onMeasure(widthSpec, heightSpec);
            if ((mViewFlags & FLAG_SET_MEASURED_SIZE) == 0) {
                throw new IllegalStateException(getClass().getName()
                        + " should call setMeasuredSize() in onMeasure()");
            }
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredSize(getDefaultSize(getSuggestedMinimumWidth(), widthSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightSpec));
    }

    protected int getSuggestedMinimumHeight() {
        return getMinimumHeight() + mPaddings.top + mPaddings.bottom;
    }

    protected int getSuggestedMinimumWidth() {
        return getMinimumWidth() + mPaddings.left + mPaddings.right;
    }

    /**
     * Returns the minimum height of the view.
     *
     * @return the minimum height the view will try to be.
     *
     * @see #setMinimumHeight(int)
     */
    public int getMinimumHeight() {
        return mMinHeight;
    }

    /**
     * Sets the minimum height of the view. It is not guaranteed the view will
     * be able to achieve this minimum height (for example, if its parent layout
     * constrains it with less available height).
     *
     * @param minHeight The minimum height the view will try to be.
     *
     * @see #getMinimumHeight()
     */
    public void setMinimumHeight(int minHeight) {
        mMinHeight = minHeight;
        requestLayout();
    }

    /**
     * Returns the minimum width of the view.
     *
     * @return the minimum width the view will try to be.
     *
     * @see #setMinimumWidth(int)
     */
    public int getMinimumWidth() {
        return mMinWidth;
    }

    /**
     * Sets the minimum width of the view. It is not guaranteed the view will
     * be able to achieve this minimum width (for example, if its parent layout
     * constrains it with less available width).
     *
     * @param minWidth The minimum width the view will try to be.
     *
     * @see #getMinimumWidth()
     */
    public void setMinimumWidth(int minWidth) {
        mMinWidth = minWidth;
        requestLayout();
    }

    protected void setMeasuredSize(int width, int height) {
        mViewFlags |= FLAG_SET_MEASURED_SIZE;
        mMeasuredWidth = width;
        mMeasuredHeight = height;
    }

    public int getMeasuredWidth() {
        return mMeasuredWidth;
    }

    public int getMeasuredHeight() {
        return mMeasuredHeight;
    }

    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
    }

    /**
     * Utility to return a default begin. Uses the supplied number as left or top.
     *
     * @param size the parent size
     * @param specSize the child size
     * @param paddingBeign the padding begin
     * @param paddingFinish the padding finish
     * @param position the {@link Gravity#POSITION_BEGIN} or {@link Gravity#POSITION_CENTER} or
     *                 {@link Gravity#POSITION_FINISH}
     * @return the begin size
     */
    public static int getDefaultBegin(int size, int specSize, int paddingBeign, int paddingFinish,
            @Gravity.PositionMode int position) {
        if (position == Gravity.POSITION_FINISH) {
            return size - paddingFinish - specSize;
        } else if (position == Gravity.POSITION_CENTER) {
            return ((size - paddingBeign - paddingFinish) / 2) - (specSize / 2) + paddingBeign;
        } else {
            return paddingBeign;
        }
    }

    /**
     * Utility to return a default size. Uses the supplied size if the
     * MeasureSpec imposed no constraints. Will get larger if allowed
     * by the MeasureSpec.
     *
     * It works differently from {@link android.view.View#getDefaultSize(int, int)}.
     * For {@link MeasureSpec#AT_MOST}, size is suggestion size
     * if it is small then spec size or it is not zero,
     * otherwise spec size.
     *
     * @param size Default size for this view
     * @param measureSpec Constraints imposed by the parent
     * @return The size this view should be.
     */
    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
            case MeasureSpec.AT_MOST:
                return size == 0 ? specSize : Math.min(size, specSize);
        }
        return result;
    }

    /**
     * Utility to return a max size. Uses the supplied size if the
     * MeasureSpec imposed no constraints. Will get larger if allowed
     * by the MeasureSpec.
     *
     * It works the same as {@link android.view.View#getDefaultSize(int, int)}.
     *
     * @param size Default size for this view
     * @param measureSpec Constraints imposed by the parent
     * @return The size this view should be.
     */
    public static int getMaxSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.EXACTLY:
            case MeasureSpec.AT_MOST:
                result = specSize;
                break;
        }
        return result;
    }

    public static int getComponentSpec(int spec, int childSize) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);

        int size = Math.max(0, specSize);

        int resultSize = 0;
        int resultMode = 0;

        switch (specMode) {
            // Parent has imposed an exact size on us
            case MeasureSpec.EXACTLY:
                if (childSize >= 0) {
                    resultSize = childSize;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childSize == LayoutParams.MATCH_PARENT) {
                    // Child wants to be our size. So be it.
                    resultSize = size;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childSize == LayoutParams.WRAP_CONTENT) {
                    // Child wants to determine its own size. It can't be
                    // bigger than us.
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;

            // Parent has imposed a maximum size on us
            case MeasureSpec.AT_MOST:
                if (childSize >= 0) {
                    // Child wants a specific size... so be it
                    resultSize = childSize;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childSize == LayoutParams.MATCH_PARENT) {
                    // Child wants to be our size, but our size is not fixed.
                    // Constrain child to not be bigger than us.
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else if (childSize == LayoutParams.WRAP_CONTENT) {
                    // Child wants to determine its own size. It can't be
                    // bigger than us.
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;

            // Parent asked to see how big we want to be
            case MeasureSpec.UNSPECIFIED:
                if (childSize >= 0) {
                    // Child wants a specific size... let him have it
                    resultSize = childSize;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childSize == LayoutParams.MATCH_PARENT) {
                    // Child wants to be our size... find out how big it should
                    // be
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else if (childSize == LayoutParams.WRAP_CONTENT) {
                    // Child wants to determine its own size.... find out how
                    // big it should be
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    public static void measureComponent(GLView component, int widthSpec, int heightSpec) {
        final LayoutParams lp = component.getLayoutParams();
        final int componentWidthSpec = getComponentSpec(widthSpec, lp.width);
        final int componentHeightSpec = getComponentSpec(heightSpec, lp.height);
        component.measure(componentWidthSpec, componentHeightSpec);
    }

    public static void measureAllComponents(GLView parent, int widthSpec, int heightSpec) {
        for (int i = 0, n = parent.getComponentCount(); i < n; i++) {
            GLView component = parent.getComponent(i);
            if (component.getVisibility() == GONE) {
                continue;
            }
            measureComponent(component, widthSpec, heightSpec);
        }
    }

    public LayoutParams getLayoutParams() {
        return mLayoutParams;
    }

    public void setLayoutParams(LayoutParams params) {
        if (params == null) {
            if (mParent == null) {
                throw new NullPointerException("Layout parameters cannot be null");
            } else {
                params = mParent.generateDefaultLayoutParams();
            }
        }
        mLayoutParams = params;
        requestLayout();
    }

    protected boolean checkLayoutParams(LayoutParams p) {
        return  p != null;
    }

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p);
    }

    /**
     * Gets the bounds of the given descendant that relative to this view.
     */
    public boolean getBoundsOf(GLView descendant, Rect out) {
        int xOffset = 0;
        int yOffset = 0;
        GLView view = descendant;
        while (view != this) {
            if (view == null) return false;
            Rect bounds = view.mBounds;
            xOffset += bounds.left;
            yOffset += bounds.top;
            view = view.mParent;
        }
        out.set(xOffset, yOffset, xOffset + descendant.getWidth(),
                yOffset + descendant.getHeight());
        return true;
    }

    protected void onVisibilityChanged(GLView changedView, @Visibility int visibility) {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onVisibilityChanged(changedView, visibility);
        }
    }

    public void onAttachToRoot(GLRoot root) {
        mRoot = root;
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onAttachToRoot(root);
        }
    }

    public void onDetachFromRoot() {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onDetachFromRoot();
        }
        mRoot = null;
    }

    public void onPause() {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onPause();
        }
    }

    public void onResume() {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onResume();
        }
    }

    public void lockRendering() {
        if (mRoot != null) {
            mRoot.lockRenderThread();
        }
    }

    public void unlockRendering() {
        if (mRoot != null) {
            mRoot.unlockRenderThread();
        }
    }

    // This is for debugging only.
    // Dump the view hierarchy into log.
    void dumpTree(String prefix) {
        Log.d(TAG, prefix + getClass().getSimpleName());
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).dumpTree(prefix + "....");
        }
    }

    /**
     * A MeasureSpec encapsulates the layout requirements passed from parent to child.
     * Each MeasureSpec represents a requirement for either the width or the height.
     * A MeasureSpec is comprised of a size and a mode. There are three possible
     * modes:
     * <dl>
     * <dt>UNSPECIFIED</dt>
     * <dd>
     * The parent has not imposed any constraint on the child. It can be whatever size
     * it wants.
     * </dd>
     *
     * <dt>EXACTLY</dt>
     * <dd>
     * The parent has determined an exact size for the child. The child is going to be
     * given those bounds regardless of how big it wants to be.
     * </dd>
     *
     * <dt>AT_MOST</dt>
     * <dd>
     * The child can be as large as it wants up to the specified size.
     * </dd>
     * </dl>
     *
     * MeasureSpecs are implemented as ints to reduce object allocation. This class
     * is provided to pack and unpack the &lt;size, mode&gt; tuple into the int.
     */
    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK  = 0x3 << MODE_SHIFT;

        /**
         * Measure specification mode: The parent has not imposed any constraint
         * on the child. It can be whatever size it wants.
         */
        public static final int UNSPECIFIED = 0 << MODE_SHIFT;

        /**
         * Measure specification mode: The parent has determined an exact size
         * for the child. The child is going to be given those bounds regardless
         * of how big it wants to be.
         */
        public static final int EXACTLY     = 1 << MODE_SHIFT;

        /**
         * Measure specification mode: The child can be as large as it wants up
         * to the specified size.
         */
        public static final int AT_MOST     = 2 << MODE_SHIFT;

        /**
         * The max size allowed
         */
        public static final int MAX_SIZE    = ~MODE_MASK;

        /**
         * Creates a measure specification based on the supplied size and mode.
         *
         * The mode must always be one of the following:
         * <ul>
         *  <li>{@link #UNSPECIFIED}</li>
         *  <li>{@link #EXACTLY}</li>
         *  <li>{@link #AT_MOST}</li>
         * </ul>
         *
         * <p><strong>Note:</strong> On API level 17 and lower, makeMeasureSpec's
         * implementation was such that the order of arguments did not matter
         * and overflow in either value could impact the resulting MeasureSpec.
         * {@link android.widget.RelativeLayout} was affected by this bug.
         * Apps targeting API levels greater than 17 will get the fixed, more strict
         * behavior.</p>
         *
         * @param size the size of the measure specification
         * @param mode the mode of the measure specification
         * @return the measure specification based on size and mode
         */
        public static int makeMeasureSpec(int size, int mode) {
            return (size & ~MODE_MASK) | (mode & MODE_MASK);
        }

        /**
         * Extracts the mode from the supplied measure specification.
         *
         * @param measureSpec the measure specification to extract the mode from
         * @return {@link #UNSPECIFIED},
         *         {@link #AT_MOST} or
         *         {@link #EXACTLY}
         */
        public static int getMode(int measureSpec) {
            return (measureSpec & MODE_MASK);
        }

        /**
         * Extracts the size from the supplied measure specification.
         *
         * @param measureSpec the measure specification to extract the size from
         * @return the size in pixels defined in the supplied measure specification
         */
        public static int getSize(int measureSpec) {
            return (measureSpec & ~MODE_MASK);
        }

        /**
         * Returns a String representation of the specified measure
         * specification.
         *
         * @param measureSpec the measure specification to convert to a String
         * @return a String with the following format: "MeasureSpec: MODE SIZE"
         */
        public static String toString(int measureSpec) {
            int mode = getMode(measureSpec);
            int size = getSize(measureSpec);

            StringBuilder sb = new StringBuilder("MeasureSpec: ");

            if (mode == UNSPECIFIED)
                sb.append("UNSPECIFIED ");
            else if (mode == EXACTLY)
                sb.append("EXACTLY ");
            else if (mode == AT_MOST)
                sb.append("AT_MOST ");
            else
                sb.append(mode).append(" ");

            sb.append(size);
            return sb.toString();
        }
    }

    /**
     * The LayoutParams look like {@link android.view.ViewGroup.LayoutParams},
     * and work like it.
     */
    public static class LayoutParams {

        /**
         * Special value for the height or width requested by a View.
         * MATCH_PARENT means that the view wants to be as big as its parent,
         * minus the parent's padding, if any. Introduced in API Level 8.
         */
        public static final int MATCH_PARENT = -1;

        /**
         * Special value for the height or width requested by a View.
         * WRAP_CONTENT means that the view wants to be just large enough to fit
         * its own internal content, taking its own padding into account.
         */
        public static final int WRAP_CONTENT = -2;

        /**
         * Information about how wide the view wants to be. Can be one of the
         * constants {@link #MATCH_PARENT} or {@link #WRAP_CONTENT} or an exact size.
         */
        public int width;

        /**
         * Information about how tall the view wants to be. Can be one of the
         * constants {@link #MATCH_PARENT} or {@link #WRAP_CONTENT} or an exact size.
         */
        public int height;

        /**
         * Creates a new set of layout parameters with the specified width
         * and height.
         *
         * @param width the width, either {@link #WRAP_CONTENT},
         *        {@link #MATCH_PARENT}, or a fixed size in pixels
         * @param height the height, either {@link #WRAP_CONTENT},
         *        {@link #MATCH_PARENT}, or a fixed size in pixels
         */
        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * Copy constructor. Clones the width and height values of the source.
         *
         * @param source The layout params to copy from.
         */
        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }
    }

    /**
     * LayoutParams with gravity inside
     */
    public static class GravityLayoutParams extends LayoutParams {

        public int gravity = Gravity.NO_GRAVITY;

        public GravityLayoutParams(LayoutParams source) {
            super(source);

            if (source instanceof GravityLayoutParams) {
                gravity = ((GravityLayoutParams) source).gravity;
            }
        }

        public GravityLayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
