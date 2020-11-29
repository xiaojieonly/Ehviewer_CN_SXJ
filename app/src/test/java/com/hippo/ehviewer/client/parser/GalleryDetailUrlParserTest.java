/*
 * Copyright 2018 Hippo Seven
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
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GalleryDetailUrlParserTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "https://e-hentai.org/g/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://exhentai.org/g/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://lofi.e-hentai.org/g/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://e-hentai.org/mpv/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://exhentai.org/mpv/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://lofi.e-hentai.org/mpv/530350/8b3c7e4a21/", true, false, 530350, "8b3c7e4a21" },
        { "https://e-hentai.org/mpv/530350/8b3c7e4a21/#page1", true, false, 530350, "8b3c7e4a21" },
        { "530350/8b3c7e4a21/#page1", true, true, 0, null },

        { "530350/8b3c7e4a21", false, false, 530350, "8b3c7e4a21" },
        { "g/530350/8b3c7e4a21", false, false, 530350, "8b3c7e4a21" },
        { "530350b/8b3c7e4a21", false, true, 0, null },
        { "530350/8b3c7e4a211", false, true, 0, null },
    });
  }

  private String url;
  private boolean strict;
  private boolean isNull;
  private long gid;
  private String token;

  public GalleryDetailUrlParserTest(String url, boolean strict, boolean isNull, long gid, String token) {
    this.url = url;
    this.strict = strict;
    this.isNull = isNull;
    this.gid = gid;
    this.token = token;
  }

  @Test
  public void testParse() {
    GalleryDetailUrlParser.Result result = GalleryDetailUrlParser.parse(url, strict);
    if (isNull) {
      assertNull(result);
    } else {
      assertEquals(gid, result.gid);
      assertEquals(token, result.token);
    }
  }
}
