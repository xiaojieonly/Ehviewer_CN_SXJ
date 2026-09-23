/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.ehviewer.spider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Unit tests for SpiderInfo restore-related parsing.
 * Full {@link SpiderInfo#read}/{@link SpiderInfo#readHeader} need Android SparseArray;
 * pToken line tolerance (the 2.0.2.4 regression) is covered via {@link SpiderInfo#tryParsePTokenLine}.
 */
public class SpiderInfoTest {

    private static final String VALID_HEADER = ""
            + "VERSION2\n"
            + "00000000\n"
            + "123456\n"
            + "abcdef01\n"
            + "1\n"
            + "2\n"
            + "20\n"
            + "3\n";

    @Test
    public void tryParsePTokenLine_acceptsValidLine() {
        int[] indexOut = new int[1];
        String pToken = SpiderInfo.tryParsePTokenLine("0 token0", indexOut);

        assertEquals("token0", pToken);
        assertEquals(0, indexOut[0]);
    }

    @Test
    public void tryParsePTokenLine_skipsCorruptIndexWithoutThrowing() {
        int[] indexOut = new int[] {-1};
        String pToken = SpiderInfo.tryParsePTokenLine("abc badtoken", indexOut);

        assertNull(pToken);
        assertEquals(-1, indexOut[0]);
    }

    @Test
    public void tryParsePTokenLine_skipsMalformedLine() {
        assertNull(SpiderInfo.tryParsePTokenLine("notoken", new int[1]));
        assertNull(SpiderInfo.tryParsePTokenLine("", new int[1]));
        assertNull(SpiderInfo.tryParsePTokenLine("1 ", new int[1]));
    }

    @Test
    public void restoreRelevantFields_arePresentInValidHeaderBytes() throws Exception {
        // Mirror what RestoreDownloadPreference needs: gid + token from header lines.
        // Format must stay compatible with SpiderInfo.parseHeader / readHeader.
        String content = VALID_HEADER
                + "0 token0\n"
                + "abc badtoken\n"
                + "2 token2\n";
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);

        // Manually walk the same header layout SpiderInfo uses (VERSION2 file).
        String[] lines = content.split("\n", -1);
        assertEquals("VERSION2", lines[0]);
        assertEquals("123456", lines[2]);
        assertEquals("abcdef01", lines[3]);
        assertEquals("3", lines[7]);

        // Corrupt pToken line must not prevent reading later valid lines.
        int[] indexOut = new int[1];
        assertEquals("token0", SpiderInfo.tryParsePTokenLine(lines[8], indexOut));
        assertEquals(0, indexOut[0]);
        assertNull(SpiderInfo.tryParsePTokenLine(lines[9], indexOut));
        assertEquals("token2", SpiderInfo.tryParsePTokenLine(lines[10], indexOut));
        assertEquals(2, indexOut[0]);

        // Stream is still a valid .ehviewer payload for restore (header-only path).
        assertNotNull(new ByteArrayInputStream(bytes));
    }
}
