package com.hippo.ehviewer.spider;

/**
 * Pure helpers for atomically publishing downloads: a downloaded image is
 * written into a temporary file and only renamed onto its final name after
 * every check passed, so a partially downloaded file can never be picked up
 * as a finished image.
 */
public final class DownloadVerifier {

    /**
     * Suffix of the temporary file used while a download is in flight.
     */
    public static final String TEMP_SUFFIX = ".tmp";

    private DownloadVerifier() {
    }

    /**
     * @return the temporary file name used while downloading {@code finalFilename}.
     */
    public static String tempFilename(String finalFilename) {
        return finalFilename + TEMP_SUFFIX;
    }

    /**
     * Whether the received byte count is acceptable for the given content
     * length. A negative content length means the length is unknown (for
     * example a chunked response); it is treated as acceptable here, the
     * caller decides the fallback policy.
     */
    public static boolean isSizeSufficient(long contentLength, long receivedSize) {
        return contentLength < 0 || receivedSize >= contentLength;
    }
}
