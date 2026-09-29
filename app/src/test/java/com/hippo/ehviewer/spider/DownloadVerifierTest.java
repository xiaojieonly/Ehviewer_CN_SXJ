package com.hippo.ehviewer.spider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadVerifierTest {

    @Test
    public void tempFilenameAppendsSuffix() {
        assertEquals("00000001.jpg.tmp", DownloadVerifier.tempFilename("00000001.jpg"));
        assertEquals("00000123.webp.tmp", DownloadVerifier.tempFilename("00000123.webp"));
    }

    @Test
    public void tempFilenameDiffersFromFinalName() {
        String finalName = "00000042.gif";
        String tempName = DownloadVerifier.tempFilename(finalName);
        assertFalse(tempName.equals(finalName));
        assertTrue(tempName.endsWith(DownloadVerifier.TEMP_SUFFIX));
    }

    @Test
    public void sizeSufficientAcceptsExactLength() {
        assertTrue(DownloadVerifier.isSizeSufficient(1024L, 1024L));
    }

    @Test
    public void sizeSufficientRejectsShortBody() {
        assertFalse(DownloadVerifier.isSizeSufficient(1024L, 1023L));
        assertFalse(DownloadVerifier.isSizeSufficient(1L, 0L));
    }

    @Test
    public void sizeSufficientAcceptsLongerBody() {
        assertTrue(DownloadVerifier.isSizeSufficient(1024L, 1025L));
    }

    @Test
    public void sizeSufficientAcceptsUnknownLength() {
        assertTrue(DownloadVerifier.isSizeSufficient(-1L, 0L));
        assertTrue(DownloadVerifier.isSizeSufficient(-1L, 4096L));
    }

    @Test
    public void sizeSufficientHandlesZeroExpected() {
        assertTrue(DownloadVerifier.isSizeSufficient(0L, 0L));
    }
}
