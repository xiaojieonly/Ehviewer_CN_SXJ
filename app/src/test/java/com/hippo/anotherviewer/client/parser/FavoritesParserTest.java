/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.client.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import okio.BufferedSource;
import okio.Okio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * R4-8 / W3 task #12: end-to-end DOM-alignment proof for the cloud-favorites
 * page. The three fixtures are frozen historical captures of the
 * {@code favorites.php} response, so this test proves {@link FavoritesParser}
 * succeeds on exactly the page shape that previously caused "解析失败".
 *
 * Fixtures (FAVORITES corpus {0:[1003,2002], 1:[2001], 2:[3002]}):
 *   FavoritesParserDefault.html -> favorites.php            (all, 4 rows)
 *   FavoritesParserFolder0.html -> favorites.php?favcat=0   (2 rows)
 *   FavoritesParserEmpty.html   -> favorites.php?favcat=5   (empty folder)
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class FavoritesParserTest {

    private static String load(String file) throws IOException {
        InputStream resource = FavoritesParserTest.class.getResourceAsStream(file);
        assertNotNull("missing fixture " + file, resource);
        BufferedSource source = Okio.buffer(Okio.source(resource));
        return source.readUtf8();
    }

    /** The 10-slot folder bar is identical across all three fixtures. */
    private static void assertFolderBar(FavoritesParser.Result result) {
        assertNotNull(result.catArray);
        assertNotNull(result.countArray);
        assertEquals(10, result.catArray.length);
        assertEquals(10, result.countArray.length);
        for (int i = 0; i < 10; i++) {
            assertEquals("Favorites " + i, result.catArray[i]);
        }
        assertEquals(2, result.countArray[0]);
        assertEquals(1, result.countArray[1]);
        assertEquals(1, result.countArray[2]);
        for (int i = 3; i < 10; i++) {
            assertEquals(0, result.countArray[i]);
        }
    }

    @Test
    public void testParseDefaultAllFavorites() throws Exception {
        FavoritesParser.Result result = FavoritesParser.parse(load("FavoritesParserDefault.html"));

        assertFolderBar(result);
        assertEquals("f", result.favOrder);
        assertEquals(1, result.pages);
        assertEquals(4, result.galleryInfoList.size());
        assertEquals(1003L, result.galleryInfoList.get(0).gid);
        assertEquals(2002L, result.galleryInfoList.get(1).gid);
        assertEquals(2001L, result.galleryInfoList.get(2).gid);
        assertEquals(3002L, result.galleryInfoList.get(3).gid);
        for (int i = 0; i < result.galleryInfoList.size(); i++) {
            assertNotNull(result.galleryInfoList.get(i).title);
            assertNotNull(result.galleryInfoList.get(i).thumb);
        }
    }

    @Test
    public void testParseSingleFolder() throws Exception {
        FavoritesParser.Result result = FavoritesParser.parse(load("FavoritesParserFolder0.html"));

        assertFolderBar(result);
        assertEquals("f", result.favOrder);
        assertEquals(1, result.pages);
        assertEquals(2, result.galleryInfoList.size());
        assertEquals(1003L, result.galleryInfoList.get(0).gid);
        assertEquals(2002L, result.galleryInfoList.get(1).gid);
    }

    @Test
    public void testParseEmptyFolder() throws Exception {
        FavoritesParser.Result result = FavoritesParser.parse(load("FavoritesParserEmpty.html"));

        // Folder bar still parses (10 slots + All), but the list is empty and
        // pages=0 via the "No hits found" path — NOT a "No gallery" exception.
        assertFolderBar(result);
        assertEquals(0, result.pages);
        assertNotNull(result.galleryInfoList);
        assertTrue(result.galleryInfoList.isEmpty());
    }
}
