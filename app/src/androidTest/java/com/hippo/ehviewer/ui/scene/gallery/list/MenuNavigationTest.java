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

package com.hippo.ehviewer.ui.scene.gallery.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.R;
import com.hippo.widget.ContentLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Exercises the real toolbar and drawer with offline gallery cards. */
public class MenuNavigationTest {
    private Instrumentation instrumentation;
    private GalleryNavigationTestActivity activity;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.setInTouchMode(false);
        Intent intent = new Intent(instrumentation.getTargetContext(),
                GalleryNavigationTestActivity.class).putExtra("menu", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (GalleryNavigationTestActivity) instrumentation.startActivitySync(intent);
        awaitLayout();
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> activity.finish());
        instrumentation.waitForIdleSync();
    }

    private void awaitLayout() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> {
                RecyclerView list = activity.recyclerView;
                ready[0] = activity.hasWindowFocus() && list.getWidth() > 0
                        && !list.isLayoutRequested() && !list.hasPendingAdapterUpdates()
                        && !list.isAnimating() && list.findViewHolderForAdapterPosition(0) != null;
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("The menu fixture must have window focus and laid-out gallery cards");
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
        SystemClock.sleep(350);
        instrumentation.runOnMainSync(() -> assertTrue(activity.hasWindowFocus()));
    }

    private void focusFirstCard() {
        instrumentation.runOnMainSync(() -> assertTrue(
                activity.recyclerView.findViewHolderForAdapterPosition(0).itemView.requestFocus()));
    }

    private void assertMenuFocused() {
        instrumentation.runOnMainSync(() -> {
            assertTrue("Left at the first column must reach the menu button",
                    activity.toolbar.isFocused());
            assertTrue(activity.toolbar.getBackground().isStateful());
            assertEquals(0f, activity.searchBar.getTranslationY(), 0.01f);
        });
    }

    private void assertItemFocused(int id) {
        instrumentation.runOnMainSync(() -> assertTrue(
                "The drawer item must receive focus", activity.navigation.findViewById(id).hasFocus()));
    }

    private void checkMenuFlow() {
        instrumentation.runOnMainSync(() -> activity.enablePaging(1));
        awaitLayout();
        focusFirstCard();
        int requests = activity.helper.requests;
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(requests + 1, activity.helper.requests);
        assertEquals(ContentLayout.ContentHelper.TYPE_REFRESH, activity.helper.requestType);
        instrumentation.runOnMainSync(() -> activity.completeCurrentPage(1));
        awaitLayout();

        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertMenuFocused();
        assertEquals(requests + 1, activity.helper.requests);
        int[] items = {R.id.nav_homepage, R.id.nav_subscription, R.id.nav_whats_hot,
                R.id.nav_top_lists};
        int index = 0;
        for (int code : new int[]{KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER}) {
            key(code);
            instrumentation.runOnMainSync(() -> assertTrue(activity.drawer.isDrawerOpen(Gravity.LEFT)));
            assertItemFocused(items[index]);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertItemFocused(items[++index]);
            int clicks = activity.menuClicks;
            key(code);
            assertEquals(clicks + 1, activity.menuClicks);
            assertEquals(items[index], activity.lastMenuId);
            instrumentation.runOnMainSync(() -> assertTrue(!activity.drawer.isDrawersVisible()));
            assertMenuFocused();
        }
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        instrumentation.runOnMainSync(() -> assertTrue(activity.recyclerView.hasFocus()));
    }

    @Test
    public void listReachesTheMenuAndConfirmsItemsWithoutLosingRefresh() {
        checkMenuFlow();
    }

    @Test
    public void gridReachesTheMenuAndConfirmsItemsWithoutLosingRefresh() {
        instrumentation.runOnMainSync(() -> activity.adapter.setType(GalleryAdapterNew.TYPE_GRID));
        awaitLayout();
        checkMenuFlow();
    }

    @Test
    public void openedDrawerContainsFocusAndBackRestoresTheMenuButton() {
        focusFirstCard();
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        key(KeyEvent.KEYCODE_BUTTON_A);
        for (int code : new int[]{KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN}) {
            key(code);
            instrumentation.runOnMainSync(() -> {
                View child = activity.drawer.getFocusedChild();
                assertEquals(Gravity.LEFT,
                        ((DrawerLayout.LayoutParams) child.getLayoutParams()).gravity);
            });
        }
        key(KeyEvent.KEYCODE_BACK);
        instrumentation.runOnMainSync(() -> assertTrue(!activity.drawer.isDrawersVisible()));
        assertMenuFocused();
    }

    @Test
    public void scrollingKeepsTheToolbarVisibleAndItsMenuReachable() {
        focusFirstCard();
        for (int i = 0; i < 8; i++) {
            key(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        instrumentation.runOnMainSync(() -> {
            assertTrue(activity.recyclerView.computeVerticalScrollOffset() > 0);
            assertEquals(0f, activity.searchBar.getTranslationY(), 0.01f);
        });
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertMenuFocused();
    }

    @Test
    public void joystickAxesReachTheMenuAndMoveBetweenItems() {
        focusFirstCard();
        moveAxis(MotionEvent.AXIS_X, -1f);
        assertMenuFocused();
        key(KeyEvent.KEYCODE_BUTTON_A);
        assertItemFocused(R.id.nav_homepage);
        moveAxis(MotionEvent.AXIS_HAT_Y, 1f);
        assertItemFocused(R.id.nav_subscription);
    }

    private void moveAxis(int axis, float value) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.setAxisValue(axis, value);
        int deviceId = -1;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && device.supportsSource(InputDevice.SOURCE_JOYSTICK)) {
                deviceId = id;
                break;
            }
        }
        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1, 1, deviceId, 0, InputDevice.SOURCE_JOYSTICK, 0);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        event.recycle();
        coords.setAxisValue(axis, 0f);
        event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1, 1, deviceId, 0, InputDevice.SOURCE_JOYSTICK, 0);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        event.recycle();
        SystemClock.sleep(350);
    }
}
