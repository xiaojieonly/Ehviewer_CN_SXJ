package com.hippo.ehviewer.ui.inset;

import static org.junit.Assert.assertEquals;

import androidx.core.graphics.Insets;

import org.junit.Test;

public class WindowInsetHelperTest {

    @Test
    public void resolveInsetsReturnsSystemBarsWhenNoCutoutExists() {
        Insets resolved = WindowInsetHelper.maxInsets(
                Insets.of(1, 2, 3, 4),
                null
        );

        assertEquals(1, resolved.left);
        assertEquals(2, resolved.top);
        assertEquals(3, resolved.right);
        assertEquals(4, resolved.bottom);
    }

    @Test
    public void resolveInsetsPrefersLargestCutoutEdges() {
        Insets resolved = WindowInsetHelper.maxInsets(
                Insets.of(1, 20, 3, 4),
                Insets.of(10, 2, 30, 40)
        );

        assertEquals(10, resolved.left);
        assertEquals(20, resolved.top);
        assertEquals(30, resolved.right);
        assertEquals(40, resolved.bottom);
    }
}
