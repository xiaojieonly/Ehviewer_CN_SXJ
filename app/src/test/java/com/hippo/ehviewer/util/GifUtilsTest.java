package com.hippo.ehviewer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;

public class GifUtilsTest {

    @Test
    public void testStaticGifIsNotAnimated() throws IOException {
        byte[] gif = createGif(false, 1, 0);
        assertFalse(GifUtils.isAnimatedGif(new ByteArrayInputStream(gif)));
        assertEquals(0, GifUtils.getGifAnimationDuration(new ByteArrayInputStream(gif)));
    }

    @Test
    public void testAnimatedGifIsDetected() throws IOException {
        byte[] gif = createGif(true, 2, 120);
        assertTrue(GifUtils.isAnimatedGif(new ByteArrayInputStream(gif)));
    }

    @Test
    public void testAnimatedGifDuration() throws IOException {
        byte[] gif = createGif(true, 2, 120);
        assertEquals(240, GifUtils.getGifAnimationDuration(new ByteArrayInputStream(gif)));
    }

    private byte[] createGif(boolean animated, int frameCount, int delay) {
        int size = 6 + 7 + frameCount * (animated ? 20 : 12) + 1;
        byte[] data = new byte[size];
        System.arraycopy("GIF89a".getBytes(), 0, data, 0, 6);
        data[6] = 1;
        data[7] = 0;
        data[8] = 1;
        data[9] = 0;
        data[10] = 0;
        data[11] = 0;
        data[12] = 0;

        int offset = 13;
        for (int i = 0; i < frameCount; i++) {
            if (animated) {
                data[offset++] = 0x21;
                data[offset++] = (byte) 0xF9;
                data[offset++] = 0x04;
                data[offset++] = 0x00;
                data[offset++] = (byte) (delay & 0xFF);
                data[offset++] = (byte) ((delay >> 8) & 0xFF);
                data[offset++] = 0x00;
                data[offset++] = 0x00;
            }

            data[offset++] = 0x2C;
            data[offset++] = 0x00;
            data[offset++] = 0x00;
            data[offset++] = 0x00;
            data[offset++] = 0x00;
            data[offset++] = 0x01;
            data[offset++] = 0x00;
            data[offset++] = 0x01;
            data[offset++] = 0x00;
            data[offset++] = 0x00;
            data[offset++] = 0x02;
            data[offset++] = 0x00;
        }

        data[offset] = 0x3B;
        return data;
    }
}
