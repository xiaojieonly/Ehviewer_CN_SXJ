package com.hippo.ehviewer.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BoundedFrameLayout extends FrameLayout {

    private int maxWidthPx = Integer.MAX_VALUE;

    public BoundedFrameLayout(@NonNull Context context) {
        super(context);
    }

    public BoundedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        readAttrs(context, attrs);
    }

    public BoundedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        readAttrs(context, attrs);
    }

    private void readAttrs(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.maxWidth});
        maxWidthPx = typedArray.getDimensionPixelSize(0, Integer.MAX_VALUE);
        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (maxWidthPx > 0 && widthMode != MeasureSpec.UNSPECIFIED) {
            widthSize = Math.min(widthSize, maxWidthPx);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
