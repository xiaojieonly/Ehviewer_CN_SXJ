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

    /**
     * Legacy image URLs embed the authoritative byte count of the served
     * file: ".../<sha1>-<bytes>-<width>-<height>-<ext>/...".
     */
    private static final java.util.regex.Pattern SIZE_IN_URL =
            java.util.regex.Pattern.compile("/[0-9a-fA-F]{40}-(\\d+)-(\\d+)-(\\d+)-[a-z]+(?:/|\\?|$)");

    private DownloadVerifier() {
    }

    /**
     * @return the temporary file name used while downloading {@code finalFilename}.
     */
    public static String tempFilename(String finalFilename) {
        return finalFilename + TEMP_SUFFIX;
    }

    /**
     * Byte count embedded in an image URL, or -1 when the URL does not carry
     * one (for example modern "?k=&t=" URLs).
     */
    public static long expectedSizeFromUrl(String url) {
        if (url == null) {
            return -1L;
        }
        java.util.regex.Matcher m = SIZE_IN_URL.matcher(url);
        if (!m.find()) {
            return -1L;
        }
        try {
            long size = Long.parseLong(m.group(1));
            // Implausible values are treated as "no information".
            if (size < 1024L || size > 512L * 1024L * 1024L) {
                return -1L;
            }
            return size;
        } catch (NumberFormatException e) {
            return -1L;
        }
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
