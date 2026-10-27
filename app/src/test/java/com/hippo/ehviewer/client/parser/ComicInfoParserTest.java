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

package com.hippo.ehviewer.client.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ComicInfoParserTest {

    private static final String SAMPLE = ""
            + "<?xml version='1.0' encoding='UTF-8' ?>\n"
            + "<ComicInfo>\n"
            + "  <Series>Sanitized Sample Gallery</Series>\n"
            + "  <Web>https://exhentai.org/g/900000002/0000000000</Web>\n"
            + "  <PageCount>77</PageCount>\n"
            + "</ComicInfo>\n";

    @Test
    public void parse_readsGidAndTokenFromWeb() {
        GalleryDetailUrlParser.Result result = ComicInfoParser.parse(SAMPLE);

        assertNotNull(result);
        assertEquals(900000002L, result.gid);
        assertEquals("0000000000", result.token);
    }

    @Test
    public void parse_readsFromStream() {
        GalleryDetailUrlParser.Result result = ComicInfoParser.parse(
                new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(result);
        assertEquals(900000002L, result.gid);
        assertEquals("0000000000", result.token);
    }

    @Test
    public void parse_returnsNullWithoutWeb() {
        assertNull(ComicInfoParser.parse("<ComicInfo><Series>Only a title</Series></ComicInfo>"));
        assertNull(ComicInfoParser.parse(""));
        assertNull(ComicInfoParser.parse((String) null));
    }
}
