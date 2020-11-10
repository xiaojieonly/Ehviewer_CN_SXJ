/*
 * Copyright 2019 Hippo Seven
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

import com.hippo.ehviewer.client.exception.ParseException;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class GalleryPageApiParserTest {

  @Test
  public void testParse() throws IOException, ParseException {
    InputStream resource = GalleryPageApiParserTest.class.getResourceAsStream("GalleryPageApiParserTest.json");
    BufferedSource source = Okio.buffer(Okio.source(resource));
    String body = source.readUtf8();

    GalleryPageApiParser.Result result = GalleryPageApiParser.parse(body);
    assertEquals("http://69.30.203.46:60111/h/6047fa2f194742f6fa541ec1f631ec3ab438f960-183117-1280-960-jpg/keystamp=1550291100-c4438f48c8;fileindex=67379169;xres=1280/Valentines_2019_002.jpg", result.imageUrl);
    assertEquals("15151-430636", result.skipHathKey);
    assertEquals("https://e-hentai.org/fullimg.php?gid=1366222&page=3&key=puxxvyg98a4", result.originImageUrl);
  }
}
