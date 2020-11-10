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

package com.hippo.ehviewer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Environment;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class CardBgTest extends TestCase {

    private static final float[] TOP_ALPHA = {
            0f, // Dump
            0.12f,
            0.16f,
            0.19f,
            0.25f,
            0.30f,
    };

    private static final float[] TOP_OFFSET = {
            0f, // Dump
            1f,
            3f,
            10f,
            14f,
            19f,
    };

    private static final float[] TOP_BLUR = {
            0f, // Dump
            1.5f,
            3f,
            10f,
            14f,
            19f,
    };

    private static final float[] BOTTOM_ALPHA = {
            0f, // Dump
            0.24f,
            0.23f,
            0.23f,
            0.22f,
            0.22f,
    };

    private static final float[] BOTTOM_OFFSET = {
            0f, // Dump
            1f,
            3f,
            6f,
            10f,
            15f,
    };

    private static final float[] BOTTOM_BLUR = {
            0f, // Dump
            1f,
            3f,
            3f,
            5f,
            6f,
    };

    private static final int MAX_SIZE = 200;


    public void testGen() throws FileNotFoundException {
        genCardBg(Color.WHITE, 2, 2);
    }

    private Path getPath(float radius, float base) {
        RectF rectF = new RectF();
        Path path = new Path();
        path.moveTo(base / 2, -base / 2 - radius);
        rectF.set(base / 2 - radius, -base / 2 - radius, base / 2 + radius, -base / 2 + radius);
        path.arcTo(rectF, -90, 90);
        path.lineTo(base / 2 + radius, base / 2);
        rectF.set(base / 2 - radius, base / 2 - radius, base / 2 + radius, base / 2 + radius);
        path.arcTo(rectF, 0, 90);
        path.lineTo(-base / 2, base / 2 + radius);
        rectF.set(-base / 2 - radius, base / 2 - radius, -base / 2 + radius, base / 2 + radius);
        path.arcTo(rectF, 90, 90);
        path.lineTo(-base / 2 - radius, -base / 2);
        rectF.set(-base / 2 - radius, -base / 2 - radius, -base / 2 + radius, -base / 2 + radius);
        path.arcTo(rectF, 180, 90);
        path.close();
        return path;
    }

    private void genCardBg(int color, float radius, int elevation) throws FileNotFoundException {

        float base = 4;
        float topAlpha = TOP_ALPHA[elevation];
        float topOffset = TOP_OFFSET[elevation];
        float topBlur = TOP_BLUR[elevation];
        float bottomAlpha = BOTTOM_ALPHA[elevation];
        float bottomOffset = BOTTOM_OFFSET[elevation];
        float bottomBlur = BOTTOM_BLUR[elevation];


        float ratio = 3;


        doGenCardBg(new FileOutputStream(new File(Environment.getExternalStorageDirectory(),  "test.png")),
                color, radius * ratio, base * ratio,
                topAlpha, topOffset * ratio, topBlur * ratio,
                bottomAlpha, bottomOffset * ratio, bottomBlur * ratio);


    }

    private int getColor(float alpha) {
        return Color.argb((int) (alpha * 0xff), 0, 0, 0);
    }

    private void doGenCardBg(OutputStream os, int color, float radius, float base,
            float topAlpha, float topOffset, float topBlur,
            float bottomAlpha, float bottomOffset, float bottomBlur) throws FileNotFoundException {

        Path path = getPath(radius, base);
        path.offset(MAX_SIZE / 2, MAX_SIZE / 2);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(color);
        Bitmap bitmap = Bitmap.createBitmap(MAX_SIZE, MAX_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw bottom
        paint.setShadowLayer(bottomBlur, 0, bottomOffset, getColor(bottomAlpha));
        canvas.drawPath(path, paint);

        // Draw top
        paint.setShadowLayer(topBlur, 0, topOffset, getColor(topAlpha));
        canvas.drawPath(path, paint);

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
    }
}
