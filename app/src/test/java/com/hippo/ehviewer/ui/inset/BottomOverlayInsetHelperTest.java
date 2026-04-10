package com.hippo.ehviewer.ui.inset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BottomOverlayInsetHelperTest {

    @Test
    public void zeroBottomInsetKeepsLegacySpacing() {
        BottomOverlayInsetHelper.Baseline baseline = BottomOverlayInsetHelper.capture(12, 70, 8, 4, 70);

        BottomOverlayInsetHelper.Resolved resolved = BottomOverlayInsetHelper.resolve(baseline, 0, true, false);

        assertEquals(82, resolved.recyclerBottomPadding);
        assertEquals(8, resolved.fastScrollerBottomPadding);
        assertEquals(4, resolved.paginationBottomPadding);
        assertEquals(70, resolved.fabBottomPadding);
        assertEquals(0, resolved.appliedBottomInset);
        assertTrue(resolved.paginationVisible);
        assertFalse(resolved.fabExpanded);
    }

    @Test
    public void largeBottomInsetAddsInsetOnceToBottomSurfaces() {
        BottomOverlayInsetHelper.Baseline baseline = BottomOverlayInsetHelper.capture(12, 70, 8, 4, 70);

        BottomOverlayInsetHelper.Resolved resolved = BottomOverlayInsetHelper.resolve(baseline, 48, true, false);

        assertEquals(130, resolved.recyclerBottomPadding);
        assertEquals(56, resolved.fastScrollerBottomPadding);
        assertEquals(52, resolved.paginationBottomPadding);
        assertEquals(118, resolved.fabBottomPadding);
    }

    @Test
    public void hiddenPaginationKeepsBaselinePadding() {
        BottomOverlayInsetHelper.Baseline baseline = BottomOverlayInsetHelper.capture(12, 70, 8, 6, 70);

        BottomOverlayInsetHelper.Resolved resolved = BottomOverlayInsetHelper.resolve(baseline, 36, false, false);

        assertEquals(118, resolved.recyclerBottomPadding);
        assertEquals(44, resolved.fastScrollerBottomPadding);
        assertEquals(6, resolved.paginationBottomPadding);
        assertEquals(106, resolved.fabBottomPadding);
        assertFalse(resolved.paginationVisible);
    }

    @Test
    public void repeatedInsetDeliveryDoesNotStackOffsets() {
        BottomOverlayInsetHelper.Baseline baseline = BottomOverlayInsetHelper.capture(10, 70, 8, 0, 70);

        BottomOverlayInsetHelper.Resolved first = BottomOverlayInsetHelper.resolve(baseline, 24, true, false);
        BottomOverlayInsetHelper.Resolved second = BottomOverlayInsetHelper.resolve(baseline, 24, true, true);

        assertEquals(first.recyclerBottomPadding, second.recyclerBottomPadding);
        assertEquals(first.fastScrollerBottomPadding, second.fastScrollerBottomPadding);
        assertEquals(first.paginationBottomPadding, second.paginationBottomPadding);
        assertEquals(first.fabBottomPadding, second.fabBottomPadding);
        assertFalse(first.fabExpanded);
        assertTrue(second.fabExpanded);
    }

    @Test
    public void negativeInputsClampToSafeValues() {
        BottomOverlayInsetHelper.Baseline baseline = BottomOverlayInsetHelper.capture(-12, -70, 8, -4, 70);

        BottomOverlayInsetHelper.Resolved resolved = BottomOverlayInsetHelper.resolve(baseline, -24, true, true);

        assertEquals(0, resolved.recyclerBottomPadding);
        assertEquals(8, resolved.fastScrollerBottomPadding);
        assertEquals(0, resolved.paginationBottomPadding);
        assertEquals(70, resolved.fabBottomPadding);
        assertEquals(0, resolved.appliedBottomInset);
        assertTrue(resolved.fabExpanded);
    }
}
