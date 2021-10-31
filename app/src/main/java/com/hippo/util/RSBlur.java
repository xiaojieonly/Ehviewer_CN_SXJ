package com.hippo.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.view.ViewTreeObserver;

public class RSBlur {
    /**
     *
     * @param context 上下文
     * @param lowerView 底层view控件
     * @param upperView 需要模糊背景的控件
     */
    public static void doBlur(final Context context, final View lowerView, final View upperView) {
        lowerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                lowerView.getViewTreeObserver().removeOnPreDrawListener(this);
                lowerView.buildDrawingCache();
                Bitmap bmp = lowerView.getDrawingCache();
                doBlur(context, bmp, upperView, true);
                return true;
            }
        });
    }
    /**
     *
     * @param context 上下文
     * @param bkg 原图
     * @param view 需要模糊背景的控件
     * @param isDownScale 是否降低等级，优化效率（建议开启）
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void doBlur(Context context, Bitmap bkg, View view, boolean isDownScale) {
        float scaleFactor = 1;
        float radius = 20;

        if (isDownScale) {
            scaleFactor = 8;
            radius = 2;
        }

        Bitmap overlay = Bitmap.createBitmap((int) (view.getMeasuredWidth() / scaleFactor),
                (int) (view.getMeasuredHeight() / scaleFactor), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(overlay);

        canvas.translate(-view.getLeft() / scaleFactor, -view.getTop() / scaleFactor);
        canvas.scale(1 / scaleFactor, 1 / scaleFactor);
        Paint paint = new Paint();
        paint.setFlags(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bkg, 0, 0, paint);

        RenderScript rs = RenderScript.create(context);

        Allocation overlayAlloc = Allocation.createFromBitmap(
                rs, overlay);

        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(
                rs, overlayAlloc.getElement());

        blur.setInput(overlayAlloc);

        blur.setRadius(radius);

        blur.forEach(overlayAlloc);

        overlayAlloc.copyTo(overlay);

        view.setBackground(new BitmapDrawable(context.getResources(), overlay));

        rs.destroy();
    }
}
