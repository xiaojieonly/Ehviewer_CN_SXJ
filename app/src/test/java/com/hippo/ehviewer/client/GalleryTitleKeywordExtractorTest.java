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

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GalleryTitleKeywordExtractorTest {

    @Parameterized.Parameters(name = "{index}: {0} -> {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {null, null},
                {"", null},
                {"   ", null},
                {"[Alpha] 【Beta】 （Gamma） (Delta)", "Alpha"},
                {"(Low) [High]", "High"},
                {"(first class[sec class])", "sec class"},
                {"[first class(sec class)]", "first class"},
                {"[outer[inner]]", "outer"},
                {"[wrong) (right)", "right"},
                {"[AI Generated] [Good Name]", "Good Name"},
                {"[Artist(sec)] (RealName)", "RealName"},
                {"[Artist(sec)]", null},
                {"[Patreon(foo)] Other", "Other"},
                {"[dEcEnSoReD] 【Creator】", "Creator"},
                {"[Pixiv Works]", "Pixiv Works"},
                {"[ AI   Generated ] (Name)", "Name"},
                {"one two three four five six seven [Creator Name]", "Creator Name"},
                {"one two three four five six seven eight [Late]", "one"},
                {"one two three four five six [AI Generated] [Creator Name]", "Creator Name"},
                {"TargetWW [AI Generated] Remaining text", "TargetWW"},
                {"[AI Generated] Remaining text", "Remaining"},
                {"AI Generated Remaining", "Remaining"},
                {"Pixiv Remaining", "Remaining"},
                {"Pixivision Remaining", "Pixivision"},
                {"[Patreon] [Fanbox] [Artist]", null},
                {"[MiXeD.Name-01]", "MiXeD.Name-01"},
                {"（Full Width） (ASCII)", "Full Width"},
                {"[Decensored v2]", "Decensored v2"}
        });
    }

    private final String title;
    private final String expected;

    public GalleryTitleKeywordExtractorTest(String title, String expected) {
        this.title = title;
        this.expected = expected;
    }

    @Test
    public void extractsArtistKeyword() {
        assertEquals(expected,
                GalleryTitleKeywordExtractor.extractArtistKeyword(title));
    }
}
