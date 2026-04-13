package com.hippo.ehviewer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;

public class WebpUtilsTest {

    @Test
    public void testStaticWebpIsNotAnimated() throws IOException {
        byte[] header = createWebpHeader(false);
        assertFalse(WebpUtils.isAnimatedWebp(new ByteArrayInputStream(header)));
    }

    @Test
    public void testAnimatedWebpIsDetected() throws IOException {
        byte[] header = createWebpHeader(true);
        assertTrue(WebpUtils.isAnimatedWebp(new ByteArrayInputStream(header)));
    }

    @Test
    public void testAnimatedWebpDuration() throws IOException {
        byte[] webp = createAnimatedWebpFile(300);
        assertEquals(300L, WebpUtils.getAnimatedWebpDuration(new ByteArrayInputStream(webp)));
    }

    private byte[] createWebpHeader(boolean animated) {
        byte[] header = new byte[30];
        System.arraycopy("RIFF".getBytes(), 0, header, 0, 4);
        header[4] = 20;
        header[5] = 0;
        header[6] = 0;
        header[7] = 0;
        System.arraycopy("WEBP".getBytes(), 0, header, 8, 4);
        System.arraycopy("VP8X".getBytes(), 0, header, 12, 4);
        header[16] = 10;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = animated ? (byte) 0x02 : (byte) 0x00;
        return header;
    }

    private byte[] createAnimatedWebpFile(int delay) {
        byte[] file = new byte[58];
        System.arraycopy("RIFF".getBytes(), 0, file, 0, 4);
        file[4] = 50;
        file[5] = 0;
        file[6] = 0;
        file[7] = 0;
        System.arraycopy("WEBP".getBytes(), 0, file, 8, 4);
        System.arraycopy("VP8X".getBytes(), 0, file, 12, 4);
        file[16] = 10;
        file[17] = 0;
        file[18] = 0;
        file[19] = 0;
        file[20] = 0x02;

        System.arraycopy("ANMF".getBytes(), 0, file, 30, 4);
        file[34] = 20;
        file[35] = 0;
        file[36] = 0;
        file[37] = 0;
        file[38] = 0;
        file[39] = 0;
        file[40] = 0;
        file[41] = 0;
        file[42] = 0;
        file[43] = 0;
        file[44] = 0;
        file[45] = 0;
        file[46] = 0;
        file[47] = 0;
        file[48] = 0;
        file[49] = 0;
        file[55] = (byte) (delay & 0xFF);
        file[56] = (byte) ((delay >> 8) & 0xFF);
        file[57] = (byte) ((delay >> 16) & 0xFF);
        return file;
    }
}
