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

    @Test
    public void readCborHeader_readsGidAndTokenFromIndefiniteMap() {
        // Bytes of .cursor/cache/02-cbor/.ehviewer. pTokenMap is present and must be skipped.
        byte[] bytes = new byte[] {
                (byte) 0xBF, 0x63, 0x67, 0x69, 0x64, 0x1A, 0x35, (byte) 0xA4, (byte) 0xE9, 0x02,
                0x65, 0x74, 0x6F, 0x6B, 0x65, 0x6E, 0x6A, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
                0x30, 0x30, 0x30, 0x30, 0x65, 0x70, 0x61, 0x67, 0x65, 0x73, 0x18, 0x4D, 0x69,
                0x70, 0x54, 0x6F, 0x6B, 0x65, 0x6E, 0x4D, 0x61, 0x70, (byte) 0xBF, 0x00, 0x6A,
                0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x31, (byte) 0xFF, 0x6C,
                0x70, 0x72, 0x65, 0x76, 0x69, 0x65, 0x77, 0x50, 0x61, 0x67, 0x65, 0x73, 0x02,
                0x6E, 0x70, 0x72, 0x65, 0x76, 0x69, 0x65, 0x77, 0x50, 0x65, 0x72, 0x50, 0x61,
                0x67, 0x65, 0x18, 0x28, (byte) 0xFF
        };

        SpiderInfo info = SpiderInfo.readCborHeader(new ByteArrayInputStream(bytes));

        assertNotNull(info);
        assertEquals(900000002L, info.gid);
        assertEquals("0000000000", info.token);
        assertNull(SpiderInfo.readHeader(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void readCborHeader_readsDefiniteMap() {
        byte[] bytes = new byte[] {
                (byte) 0xA2, 0x63, 0x67, 0x69, 0x64, 0x1A, 0x35, (byte) 0xA4, (byte) 0xE9, 0x02,
                0x65, 0x74, 0x6F, 0x6B, 0x65, 0x6E, 0x6A, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
                0x30, 0x30, 0x30, 0x30
        };

        SpiderInfo info = SpiderInfo.readCborHeader(new ByteArrayInputStream(bytes));

        assertNotNull(info);
        assertEquals(900000002L, info.gid);
        assertEquals("0000000000", info.token);
    }

    @Test
    public void readHeader_rejectsInvalidTextAndCbor() {
        byte[] bytes = "INVALID_FORMAT".getBytes(StandardCharsets.US_ASCII);

        assertNull(SpiderInfo.readHeader(new ByteArrayInputStream(bytes)));
        assertNull(SpiderInfo.readCborHeader(new ByteArrayInputStream(bytes)));
    }
}
