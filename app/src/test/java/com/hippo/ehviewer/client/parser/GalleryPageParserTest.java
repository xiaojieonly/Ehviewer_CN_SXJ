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
public class GalleryPageParserTest {

  @Test
  public void testParse() throws IOException, ParseException {
    InputStream resource = GalleryPageParserTest.class.getResourceAsStream("GalleryPageParserTest.html");
    BufferedSource source = Okio.buffer(Okio.source(resource));
    String body = source.readUtf8();

    GalleryPageParser.Result result = GalleryPageParser.parse(body);
    assertEquals("http://108.6.41.160:2688/h/5c63e9a5810d8d9c873d9e0dfaadc4a0d70a13bf-188862-1280-879-jpg/keystamp=1550291700-145ecbbb10;fileindex=67290651;xres=1280/10.jpg", result.imageUrl);
    assertEquals("26664-430636", result.skipHathKey);
    assertEquals("https://e-hentai.org/fullimg.php?gid=1363978&page=10&key=qt2hwrx98a4", result.originImageUrl);
    assertEquals("ghz0e5m98a4", result.showKey);
  }
}
