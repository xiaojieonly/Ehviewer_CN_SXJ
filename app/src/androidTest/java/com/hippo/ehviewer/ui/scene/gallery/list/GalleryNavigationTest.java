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
import static org.junit.Assert.assertFalse;
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

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.widget.ContentLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class GalleryNavigationTest {
    private Instrumentation instrumentation;
    private GalleryNavigationTestActivity activity;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.setInTouchMode(false);
        Intent intent = new Intent(instrumentation.getTargetContext(),
                GalleryNavigationTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (GalleryNavigationTestActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        awaitLayout();
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> activity.finish());
        instrumentation.waitForIdleSync();
    }

    private void focusFirstCard() {
        instrumentation.runOnMainSync(() -> activity.recyclerView.scrollToPosition(0));
        awaitLayout();
        instrumentation.runOnMainSync(() -> {
            View first = activity.recyclerView.findViewHolderForAdapterPosition(0).itemView;
            assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,
                    ((ViewGroup) first).getDescendantFocusability());
            assertTrue(first.requestFocus());
            assertNotNull(first.getForeground());
        });
    }

    private void awaitLayout() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> {
                RecyclerView list = activity.recyclerView;
                ready[0] = activity.hasWindowFocus() && list.getWidth() > 0
                        && !list.isLayoutRequested() && !list.hasPendingAdapterUpdates()
                        && !list.isAnimating()
                        && list.findViewHolderForAdapterPosition(0) != null;
            });
            if (ready[0]) {
                return;
            }
            // Being idle does not imply that the next display frame has laid out the cards.
            SystemClock.sleep(50);
        }
        fail("The activity must have window focus and a completed card layout");
    }

    private int focusedPosition() {
        int[] position = {RecyclerView.NO_POSITION};
        instrumentation.runOnMainSync(() -> {
            View focused = activity.recyclerView.getFocusedChild();
            if (focused != null) {
                position[0] = activity.recyclerView.getChildAdapterPosition(focused);
                assertTrue(focused.isFocused());
            }
        });
        return position[0];
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
        // RecyclerView may still have a scheduled smooth scroll after the key is handled.
        SystemClock.sleep(350);
        // A manually pending refresh keeps animating, so the whole UI may never be idle.
        instrumentation.runOnMainSync(() -> assertTrue(activity.hasWindowFocus()));
    }

    private void checkMovementAndScrolling() {
        instrumentation.runOnMainSync(() -> activity.toolbar.requestFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue("Down from the toolbar must enter a card", focusedPosition() >= 0);
        focusFirstCard();
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        int right = focusedPosition();
        assertTrue("Right must reach the next column, got " + right, right > 0);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, focusedPosition());
        for (int i = 0; i < 8; i++) {
            int before = focusedPosition();
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            int after = focusedPosition();
            assertTrue("Down must move between cards, got " + before + " -> " + after,
                    after >= 0 && after != before);
        }
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(focusedPosition() > right);
    }

    @Test
    public void listNavigatesBetweenCardsAndScrolls() {
        checkMovementAndScrolling();
    }

    @Test
    public void gridNavigatesBetweenCardsAndScrolls() {
        instrumentation.runOnMainSync(() -> activity.adapter.setType(GalleryAdapterNew.TYPE_GRID));
        awaitLayout();
        checkMovementAndScrolling();
    }

    private void enablePaging(int pages) {
        instrumentation.runOnMainSync(() -> activity.enablePaging(pages));
        awaitLayout();
    }

    @Test
    public void upAtTheTopRefreshesAndHoldingTheKeyDoesNotRefreshAgain() {
        enablePaging(1);
        focusFirstCard();
        int before = activity.helper.requests;
        long downTime = SystemClock.uptimeMillis();
        instrumentation.sendKeySync(new KeyEvent(downTime, downTime,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0));
        instrumentation.runOnMainSync(() -> assertEquals(before + 1, activity.helper.requests));
        assertEquals(ContentLayout.ContentHelper.TYPE_REFRESH, activity.helper.requestType);
        assertEquals(0, activity.helper.requestPage);
        assertEquals(0, focusedPosition());

        // Complete loading while the key is still held, then send a hardware repeat.
        instrumentation.runOnMainSync(() -> activity.completeCurrentPage(1));
        awaitLayout();
        assertEquals("Refresh must retain the selected card", 0, focusedPosition());
        instrumentation.sendKeySync(new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1));
        instrumentation.sendKeySync(new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP, 0));
        instrumentation.waitForIdleSync();
        assertEquals(before + 1, activity.helper.requests);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(before + 2, activity.helper.requests);
    }

    @Test
    public void movingWithinTheListDoesNotTriggerRefresh() {
        enablePaging(1);
        focusFirstCard();
        int before = activity.helper.requests;
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(focusedPosition() > 0);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(before, activity.helper.requests);
    }

    @Test
    public void downAtTheEndRefreshesTheLastPage() {
        enablePaging(1);
        int last = activity.adapter.getItemCount() - 1;
        instrumentation.runOnMainSync(() -> activity.recyclerView.scrollToPosition(last));
        SystemClock.sleep(350);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertTrue(
                activity.recyclerView.findViewHolderForAdapterPosition(last).itemView.requestFocus()));
        int before = activity.helper.requests;
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(before + 1, activity.helper.requests);
        assertEquals(ContentLayout.ContentHelper.TYPE_REFRESH_PAGE, activity.helper.requestType);
        assertEquals(last, focusedPosition());
    }

    @Test
    public void navigatingDownLoadsTheNextPageAndRetainsTheSelectedCard() {
        enablePaging(2);
        focusFirstCard();
        int before = activity.helper.requests;
        for (int i = 0; i < 120 && activity.helper.requests == before; i++) {
            key(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        assertEquals(before + 1, activity.helper.requests);
        assertEquals(ContentLayout.ContentHelper.TYPE_NEXT_PAGE_KEEP_POS, activity.helper.requestType);
        assertEquals(1, activity.helper.requestPage);
        int selected = focusedPosition();
        assertTrue(selected >= 0);
        ArrayList<GalleryInfo> next = new ArrayList<>();
        for (int i = 121; i <= 160; i++) {
            GalleryInfo gallery = new GalleryInfo();
            gallery.gid = i;
            gallery.title = "Gallery " + i;
            next.add(gallery);
        }
        instrumentation.runOnMainSync(() -> activity.helper.complete(2, next));
        SystemClock.sleep(350);
        instrumentation.waitForIdleSync();
        assertEquals(160, activity.adapter.getItemCount());
        assertEquals(selected, focusedPosition());
    }

    @Test
    public void listHasNoFastScrollHandle() {
        instrumentation.runOnMainSync(() -> {
            assertEquals(View.GONE, activity.content.getFastScroller().getVisibility());
            assertFalse(activity.content.getFastScroller().isAttached());
        });
    }

    @Test
    public void confirmKeysOpenTheFocusedGalleryExactlyOnce() {
        focusFirstCard();
        for (int code : new int[]{KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER}) {
            int before = activity.clicks;
            key(code);
            assertEquals(before + 1, activity.clicks);
            assertEquals(1, activity.lastClickedId);
            assertEquals(0, activity.thumbnailClicks);
        }
    }

    @Test
    public void gridConfirmOpensTheFocusedGallery() {
        instrumentation.runOnMainSync(() -> activity.adapter.setType(GalleryAdapterNew.TYPE_GRID));
        awaitLayout();
        focusFirstCard();
        key(KeyEvent.KEYCODE_BUTTON_A);
        assertEquals(1, activity.clicks);
        assertEquals(1, activity.lastClickedId);
    }

    @Test
    public void holdingConfirmOpensTheExistingLongClickAction() {
        focusFirstCard();
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L);
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        instrumentation.waitForIdleSync();
        assertEquals(1, activity.longClicks);
        assertEquals(0, activity.clicks);
    }

    @Test
    public void invalidatedCardsIgnoreClicksUntilRebound() {
        instrumentation.runOnMainSync(() -> {
            View card = activity.recyclerView.findViewHolderForAdapterPosition(0).itemView;
            activity.adapter.notifyDataSetChanged();
            card.performClick();
            card.performLongClick();
            assertEquals(0, activity.clicks);
            assertEquals(0, activity.longClicks);
        });
    }

    @Test
    public void touchscreenStillOpensTheCardOnceAndKeepsThumbnailActions() {
        int[] location = new int[2];
        float[] points = new float[4];
        instrumentation.runOnMainSync(() -> {
            GalleryAdapterNew.GalleryHolder holder = (GalleryAdapterNew.GalleryHolder)
                    activity.recyclerView.findViewHolderForAdapterPosition(0);
            holder.itemView.getLocationOnScreen(location);
            points[0] = location[0] + holder.itemView.getWidth() * 0.75f;
            points[1] = location[1] + holder.itemView.getHeight() * 0.5f;
            holder.thumb.getLocationOnScreen(location);
            points[2] = location[0] + holder.thumb.getWidth() * 0.5f;
            points[3] = location[1] + holder.thumb.getHeight() * 0.5f;
        });
        tap(points[0], points[1]);
        assertEquals(1, activity.clicks);
        tap(points[2], points[3]);
        assertEquals(1, activity.clicks);
        assertEquals(1, activity.thumbnailClicks);
    }

    private void tap(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle();
        up.recycle();
        instrumentation.waitForIdleSync();
    }

    @Test
    public void joystickAndHatAxesMoveTheFocus() {
        focusFirstCard();
        moveAxis(MotionEvent.AXIS_X, 1f);
        assertTrue(focusedPosition() > 0);
        int before = focusedPosition();
        moveAxis(MotionEvent.AXIS_HAT_Y, 1f);
        assertTrue(focusedPosition() > before);
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
        instrumentation.waitForIdleSync();
    }
}
