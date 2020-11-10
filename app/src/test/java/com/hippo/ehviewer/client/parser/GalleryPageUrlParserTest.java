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
public class GalleryPageUrlParserTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "https://e-hentai.org/s/7b87643838/530350-1", true, false, 530350, "7b87643838", 0},
        { "https://exhentai.org/s/7b87643838/530350-1", true, false, 530350, "7b87643838", 0},
        { "7b87643838/530350-1", true, true, 0, null, 0},

        { "7b87643838/530350-1", false, false, 530350, "7b87643838", 0},
        { "s/7b87643838/530350-1", false, false, 530350, "7b87643838", 0},
        { "7b87643838/530350a-1", false, true, 0, null, 0},
    });
  }

  private String url;
  private boolean strict;
  private boolean isNull;
  private long gid;
  private String pToken;
  private int page;

  public GalleryPageUrlParserTest(String url, boolean strict, boolean isNull, long gid, String pToken, int page) {
    this.url = url;
    this.strict = strict;
    this.isNull = isNull;
    this.gid = gid;
    this.pToken = pToken;
    this.page = page;
  }

  @Test
  public void testParse() {
    GalleryPageUrlParser.Result result = GalleryPageUrlParser.parse(url, strict);
    if (isNull) {
      assertNull(result);
    } else {
      assertEquals(gid, result.gid);
      assertEquals(pToken, result.pToken);
      assertEquals(page, result.page);
    }
  }
}
