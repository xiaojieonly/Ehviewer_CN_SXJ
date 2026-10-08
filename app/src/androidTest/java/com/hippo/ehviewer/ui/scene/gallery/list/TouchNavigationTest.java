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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.scene.GalleryPreviewNavigationTestActivity;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.widget.ContentLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pure touchscreen events on the production card, preview, toolbar and drawer layouts. */
public class TouchNavigationTest {
    private Instrumentation instrumentation;
    private Activity activity;
    private GalleryNavigationTestActivity gallery;
    private GalleryPreviewNavigationTestActivity previews;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.setInTouchMode(true);
    }

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> activity.finish());
        }
    }

    private void startGallery(int type) {
        Intent intent = new Intent(instrumentation.getTargetContext(),
                GalleryNavigationTestActivity.class).putExtra("menu", true)
                .putExtra("layout", type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        gallery = (GalleryNavigationTestActivity) activity;
        awaitLayout();
    }

    private void startPreviews(boolean full) {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> activity.finish());
        }
        gallery = null;
        Intent intent = new Intent(instrumentation.getTargetContext(),
                GalleryPreviewNavigationTestActivity.class).putExtra("full_previews", full)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        previews = (GalleryPreviewNavigationTestActivity) activity;
        awaitLayout();
    }

    private void awaitLayout() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> {
                RecyclerView list = gallery != null ? gallery.recyclerView : previews.recyclerView;
                ready[0] = activity.hasWindowFocus() && (list != null
                        ? list.getWidth() > 0 && !list.isLayoutRequested()
                            && !list.hasPendingAdapterUpdates() && !list.isAnimating()
                        : previews.grid.getChildAt(0).getWidth() > 0
                            && !previews.grid.isLayoutRequested());
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("The touchscreen fixture must have window focus and a completed layout");
    }

    private void settle() {
        SystemClock.sleep(400);
        instrumentation.runOnMainSync(() -> {
            assertTrue(activity.hasWindowFocus());
            assertTrue("Touch input must enter touch mode", activity.getWindow().getDecorView().isInTouchMode());
        });
    }

    private float[] point(View view, float xFraction, float yFraction) {
        float[] point = new float[2];
        instrumentation.runOnMainSync(() -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + view.getWidth() * xFraction;
            point[1] = location[1] + view.getHeight() * yFraction;
            Rect visible = new Rect();
            assertTrue(view.getGlobalVisibleRect(visible));
            assertTrue("The injected touch must hit the visible view",
                    visible.contains((int) point[0], (int) point[1]));
        });
        return point;
    }

    private void pointer(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }

    private void tap(View view, float x, float y, boolean hold) {
        float[] point = point(view, x, y);
        long time = SystemClock.uptimeMillis();
        pointer(time, MotionEvent.ACTION_DOWN, point[0], point[1]);
        SystemClock.sleep(hold ? ViewConfiguration.getLongPressTimeout() + 150L : 40);
        pointer(time, MotionEvent.ACTION_UP, point[0], point[1]);
        settle();
    }

    private void drag(View view, float x1, float y1, float x2, float y2) {
        float[] start = point(view, x1, y1);
        float[] end = point(view, x2, y2);
        long time = SystemClock.uptimeMillis();
        pointer(time, MotionEvent.ACTION_DOWN, start[0], start[1]);
        for (int i = 1; i <= 12; i++) {
            SystemClock.sleep(25);
            pointer(time, MotionEvent.ACTION_MOVE, start[0] + (end[0] - start[0]) * i / 12,
                    start[1] + (end[1] - start[1]) * i / 12);
        }
        pointer(time, MotionEvent.ACTION_UP, end[0], end[1]);
        settle();
    }

    private View card() {
        View[] card = {null};
        instrumentation.runOnMainSync(() -> card[0] =
                gallery.recyclerView.findViewHolderForAdapterPosition(0).itemView);
        return card[0];
    }

    @Test
    public void listTouchAndLongPressRetainCardAndCoverActions() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        View card = card();
        tap(card, 0.8f, 0.5f, false);
        assertEquals(1, gallery.clicks);
        tap(card.findViewById(R.id.thumb_new), 0.5f, 0.5f, false);
        assertEquals(1, gallery.clicks);
        assertEquals(1, gallery.thumbnailClicks);
        tap(card, 0.8f, 0.5f, true);
        assertEquals(1, gallery.longClicks);
        assertEquals(1, gallery.clicks);
    }

    @Test
    public void gridTouchAndLongPressOpenEachActionOnlyOnce() {
        startGallery(GalleryAdapterNew.TYPE_GRID);
        View card = card();
        tap(card, 0.5f, 0.5f, false);
        assertEquals(1, gallery.clicks);
        assertEquals(0, gallery.thumbnailClicks);
        tap(card, 0.5f, 0.5f, true);
        assertEquals(1, gallery.longClicks);
        assertEquals(1, gallery.clicks);
    }

    @Test
    public void touchScrollingHidesAndRevealsTheToolbarWithoutOpeningCards() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        drag(gallery.recyclerView, 0.5f, 0.8f, 0.5f, 0.2f);
        instrumentation.runOnMainSync(() -> {
            assertTrue(gallery.recyclerView.computeVerticalScrollOffset() > 0);
            assertTrue(gallery.searchBar.getTranslationY() < 0);
        });
        drag(gallery.recyclerView, 0.5f, 0.25f, 0.5f, 0.8f);
        instrumentation.runOnMainSync(() -> assertEquals(0f, gallery.searchBar.getTranslationY(), 0.01f));
        assertEquals(0, gallery.clicks);
        assertEquals(0, gallery.longClicks);
        assertEquals(0, gallery.thumbnailClicks);
    }

    @Test
    public void pullingDownStillRefreshesWithoutControllerInput() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        instrumentation.runOnMainSync(() -> gallery.enablePaging(1));
        awaitLayout();
        int requests = gallery.helper.requests;
        drag(gallery.recyclerView, 0.5f, 0.3f, 0.5f, 0.85f);
        assertEquals(requests + 1, gallery.helper.requests);
        assertEquals(ContentLayout.ContentHelper.TYPE_REFRESH, gallery.helper.requestType);
        assertEquals(0, gallery.clicks);
        instrumentation.runOnMainSync(() -> gallery.completeCurrentPage(1));
    }

    @Test
    public void pullingUpStillLoadsTheNextPageWithoutControllerInput() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        instrumentation.runOnMainSync(() -> gallery.enablePaging(2));
        awaitLayout();
        instrumentation.runOnMainSync(() -> gallery.recyclerView.scrollToPosition(119));
        SystemClock.sleep(400);
        instrumentation.runOnMainSync(() -> gallery.recyclerView.scrollBy(0, 100000));
        awaitLayout();
        int requests = gallery.helper.requests;
        drag(gallery.recyclerView, 0.5f, 0.8f, 0.5f, 0.2f);
        assertEquals(requests + 1, gallery.helper.requests);
        assertEquals(ContentLayout.ContentHelper.TYPE_NEXT_PAGE_KEEP_POS, gallery.helper.requestType);
        assertEquals(1, gallery.helper.requestPage);
        assertEquals(0, gallery.clicks);
        instrumentation.runOnMainSync(() -> gallery.completeCurrentPage(2));
    }

    @Test
    public void menuTapsSelectItemsAndPreserveDrawerCallbacksWithoutTakingFocus() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        tap(gallery.toolbar, 0.5f, 0.5f, false);
        instrumentation.runOnMainSync(() -> {
            assertTrue(gallery.drawer.isDrawerOpen(Gravity.LEFT));
            assertFalse(gallery.navigation.findViewById(R.id.nav_homepage).hasFocus());
        });
        assertEquals(1, gallery.drawerOpens);
        tap(gallery.navigation.findViewById(R.id.nav_subscription), 0.5f, 0.5f, false);
        assertEquals(1, gallery.menuClicks);
        assertEquals(R.id.nav_subscription, gallery.lastMenuId);
        assertEquals(1, gallery.drawerCloses);
        instrumentation.runOnMainSync(() -> {
            assertFalse(gallery.drawer.isDrawersVisible());
            assertFalse(gallery.toolbar.isFocused());
        });
    }

    @Test
    public void swipingTheDrawerStillOpensAndClosesIt() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        drag(gallery.drawer, 0.01f, 0.5f, 0.4f, 0.5f);
        instrumentation.runOnMainSync(() -> assertTrue(gallery.drawer.isDrawerOpen(Gravity.LEFT)));
        assertEquals(1, gallery.drawerOpens);
        drag(gallery.drawer, 0.15f, 0.5f, 0.01f, 0.5f);
        instrumentation.runOnMainSync(() -> assertFalse(gallery.drawer.isDrawersVisible()));
        assertEquals(1, gallery.drawerCloses);
    }

    @Test
    public void toolbarAndSearchFieldRemainTouchable() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        tap(gallery.searchBar.findViewById(R.id.search_title), 0.5f, 0.5f, false);
        tap(gallery.searchBar.findViewById(R.id.search_action), 0.5f, 0.5f, false);
        assertEquals(1, gallery.toolbarTitleClicks);
        assertEquals(1, gallery.toolbarActionClicks);
        instrumentation.runOnMainSync(() -> gallery.searchBar.setState(SearchBar.STATE_SEARCH, false));
        settle();
        View edit = gallery.searchBar.findViewById(R.id.search_edit_text);
        tap(edit, 0.5f, 0.5f, false);
        instrumentation.runOnMainSync(() -> assertTrue(edit.isFocused()));
    }

    @Test
    public void detailImageTapAndLongPressKeepOpenAndRetryActions() {
        startPreviews(false);
        View image = previews.previewAt(0).findViewById(R.id.image);
        tap(image, 0.5f, 0.5f, false);
        assertEquals(1, previews.clicks);
        assertEquals(0, previews.lastOpenedPage);
        tap(image, 0.5f, 0.5f, true);
        assertEquals(1, previews.longClicks);
        assertEquals(1, previews.clicks);
        assertEquals(0, previews.moreClicks);
    }

    @Test
    public void detailCaptionAndEmptySpaceKeepTheOriginalMorePreviewsAction() {
        startPreviews(false);
        tap(previews.previewAt(0).findViewById(R.id.text), 0.5f, 0.5f, false);
        assertEquals(1, previews.moreClicks);
        assertEquals(0, previews.clicks);
        float[] point = new float[2];
        instrumentation.runOnMainSync(() -> {
            int[] location = new int[2];
            previews.previews.getLocationOnScreen(location);
            point[0] = location[0] + previews.previews.getPaddingLeft() / 2f;
            point[1] = location[1] + previews.previews.getPaddingTop() / 2f;
        });
        long time = SystemClock.uptimeMillis();
        pointer(time, MotionEvent.ACTION_DOWN, point[0], point[1]);
        pointer(time, MotionEvent.ACTION_UP, point[0], point[1]);
        settle();
        assertEquals(2, previews.moreClicks);
        assertEquals(0, previews.clicks);
    }

    @Test
    public void morePreviewsTextStillRespondsToTouch() {
        startPreviews(false);
        instrumentation.runOnMainSync(() -> previews.scrollView.fullScroll(View.FOCUS_DOWN));
        settle();
        tap(previews.more, 0.5f, 0.5f, false);
        assertEquals(1, previews.moreClicks);
        assertEquals(0, previews.clicks);
    }

    @Test
    public void fullPreviewImageAndCaptionTapsOpenOnceAndLongPressRetries() {
        startPreviews(true);
        View preview = previews.previewAt(0);
        View image = preview.findViewById(R.id.image);
        tap(image, 0.5f, 0.5f, false);
        assertEquals(1, previews.clicks);
        tap(preview.findViewById(R.id.text), 0.5f, 0.5f, false);
        assertEquals(2, previews.clicks);
        assertEquals(0, previews.lastOpenedPage);
        tap(image, 0.5f, 0.5f, true);
        assertEquals(1, previews.longClicks);
        assertEquals(2, previews.clicks);
    }

    @Test
    public void swipingBothPreviewLayoutsScrollsWithoutOpeningAnything() {
        for (boolean full : new boolean[]{false, true}) {
            startPreviews(full);
            View scroll = full ? previews.recyclerView : previews.scrollView;
            drag(scroll, 0.5f, 0.8f, 0.5f, 0.2f);
            instrumentation.runOnMainSync(() -> assertTrue(full
                    ? previews.recyclerView.computeVerticalScrollOffset() > 0
                    : previews.scrollView.getScrollY() > 0));
            assertEquals(0, previews.clicks);
            assertEquals(0, previews.longClicks);
            assertEquals(0, previews.moreClicks);
        }
    }

    @Test
    public void switchingBackToTouchRestoresToolbarHidingAndClearsCardFocus() {
        startGallery(GalleryAdapterNew.TYPE_LIST);
        instrumentation.setInTouchMode(false);
        View first = card();
        instrumentation.runOnMainSync(() -> assertTrue(first.requestFocus()));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        SystemClock.sleep(400);
        drag(gallery.recyclerView, 0.5f, 0.8f, 0.5f, 0.2f);
        instrumentation.runOnMainSync(() -> {
            assertFalse(gallery.recyclerView.hasFocus());
            assertTrue(gallery.searchBar.getTranslationY() < 0);
        });
        assertEquals(0, gallery.clicks);
    }
}
