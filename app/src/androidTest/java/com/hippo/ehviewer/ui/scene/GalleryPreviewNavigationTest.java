/*
 * Copyright 2026 antigone
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

package com.hippo.ehviewer.ui.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hippo.ehviewer.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GalleryPreviewNavigationTest {
    private Instrumentation instrumentation;
    private GalleryPreviewNavigationTestActivity activity;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.setInTouchMode(false);
        startActivity(false);
    }

    private void startActivity(boolean fullPreviews) {
        Intent intent = new Intent(instrumentation.getTargetContext(),
                GalleryPreviewNavigationTestActivity.class)
                .putExtra("full_previews", fullPreviews).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (GalleryPreviewNavigationTestActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        awaitLayout();
    }

    private void awaitLayout() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> {
                if (!activity.hasWindowFocus()) {
                    return;
                }
                if (activity.recyclerView != null) {
                    RecyclerView list = activity.recyclerView;
                    ready[0] = list.getWidth() > 0 && !list.isLayoutRequested()
                            && !list.hasPendingAdapterUpdates() && !list.isAnimating()
                            && list.findViewHolderForAdapterPosition(0) != null;
                } else {
                    ready[0] = activity.grid.getChildAt(0).getWidth() > 0
                            && !activity.grid.isLayoutRequested();
                }
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("The activity must have window focus and a completed preview layout");
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> activity.finish());
        instrumentation.waitForIdleSync();
    }

    private void useFullPreviews() {
        instrumentation.runOnMainSync(() -> activity.finish());
        instrumentation.waitForIdleSync();
        startActivity(true);
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
        SystemClock.sleep(350);
        instrumentation.waitForIdleSync();
    }

    private int focusedPage() {
        int[] page = {-1};
        instrumentation.runOnMainSync(() -> {
            View focused = activity.getCurrentFocus();
            Object tag = focused != null ? focused.getTag(R.id.index) : null;
            if (tag instanceof Integer) {
                page[0] = (Integer) tag;
                assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,
                        ((ViewGroup) focused).getDescendantFocusability());
                assertNotNull(focused.getForeground());
            }
        });
        return page[0];
    }

    private void checkDirectionsAndConfirm() {
        instrumentation.runOnMainSync(() -> activity.toolbar.requestFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue("Down from the toolbar must enter a preview", focusedPage() >= 0);
        // Native spatial navigation can enter the middle column under the toolbar.
        instrumentation.runOnMainSync(() -> assertTrue(activity.previewAt(0).requestFocus()));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(focusedPage() > 0);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, focusedPage());
        for (int i = 0; i < 8; i++) {
            int before = focusedPage();
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue(focusedPage() > before);
        }
        int before = focusedPage();
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(focusedPage() >= 0 && focusedPage() < before);
        int selected = focusedPage();
        for (int code : new int[]{KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER}) {
            int clicks = activity.clicks;
            key(code);
            assertEquals(clicks + 1, activity.clicks);
            assertEquals(selected, activity.lastOpenedPage);
        }
    }

    @Test
    public void detailPreviewsNavigateScrollAndOpenTheSelectedPage() {
        checkDirectionsAndConfirm();
        assertTrue(activity.scrollView.getScrollY() > 0);
    }

    @Test
    public void fullPreviewsNavigateScrollAndOpenTheSelectedPage() {
        useFullPreviews();
        checkDirectionsAndConfirm();
        assertTrue(activity.recyclerView.computeVerticalScrollOffset() > 0);
    }

    @Test
    public void morePreviewsIsReachableAfterTheLastThumbnail() {
        instrumentation.runOnMainSync(() -> activity.previewAt(119).requestFocus());
        SystemClock.sleep(350);
        instrumentation.waitForIdleSync();
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        instrumentation.runOnMainSync(() -> assertTrue(activity.more.isFocused()));
        key(KeyEvent.KEYCODE_BUTTON_A);
        assertEquals(1, activity.moreClicks);
        assertEquals(0, activity.clicks);
    }

    @Test
    public void holdingConfirmRetainsTheImageRetryAction() {
        instrumentation.runOnMainSync(() -> activity.previewAt(0).requestFocus());
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L);
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        instrumentation.waitForIdleSync();
        assertEquals(1, activity.longClicks);
        assertEquals(0, activity.clicks);
    }

    @Test
    public void touchingAFullPreviewStillOpensExactlyOnce() {
        useFullPreviews();
        float[] point = new float[2];
        instrumentation.runOnMainSync(() -> {
            View image = activity.previewAt(0).findViewById(R.id.image);
            int[] location = new int[2];
            image.getLocationOnScreen(location);
            point[0] = location[0] + image.getWidth() / 2f;
            point[1] = location[1] + image.getHeight() / 2f;
        });
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, point[0], point[1], 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle();
        up.recycle();
        instrumentation.waitForIdleSync();
        assertEquals(1, activity.clicks);
        assertEquals(0, activity.lastOpenedPage);
    }
}
