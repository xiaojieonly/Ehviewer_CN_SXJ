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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.hippo.ehviewer.client.EhUtils;
import edu.emory.mathcs.backport.java.util.Arrays;
import java.io.InputStream;
import java.util.List;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class GalleryListParserTest {

  private static final String E_MINIMAL = "GalleryListParserTestEMinimal.html";
  private static final String E_MINIMAL_PLUS = "GalleryListParserTestEMinimalPlus.html";
  private static final String E_COMPAT = "GalleryListParserTestECompat.html";
  private static final String E_EXTENDED = "GalleryListParserTestEExtended.html";
  private static final String E_THUMBNAIL = "GalleryListParserTestEThumbnail.html";

  private static final String EX_MINIMAL = "GalleryListParserTestExMinimal.html";
  private static final String EX_MINIMAL_PLUS = "GalleryListParserTestExMinimalPlus.html";
  private static final String EX_COMPAT = "GalleryListParserTestExCompat.html";
  private static final String EX_EXTENDED = "GalleryListParserTestExExtended.html";
  private static final String EX_THUMBNAIL = "GalleryListParserTestExThumbnail.html";

  @ParameterizedRobolectricTestRunner.Parameters(name = "{index}-{0}")
  public static List data() {
    return Arrays.asList(new Object[][] {
        { E_MINIMAL },
        { E_MINIMAL_PLUS },
        { E_COMPAT },
        { E_EXTENDED },
        { E_THUMBNAIL },
        { EX_MINIMAL },
        { EX_MINIMAL_PLUS },
        { EX_COMPAT },
        { EX_EXTENDED },
        { EX_THUMBNAIL },
    });
  }

  private String file;

  public GalleryListParserTest(String file) {
    this.file = file;
  }

  @Test
  public void testParse() throws Exception {
    InputStream resource = GalleryPageApiParserTest.class.getResourceAsStream(file);
    BufferedSource source = Okio.buffer(Okio.source(resource));
    String body = source.readUtf8();

    GalleryListParser.Result result = GalleryListParser.parse(body);

    assertEquals(25, result.galleryInfoList.size());

    result.galleryInfoList.forEach(gi -> {
      assertNotEquals(0, gi.gid);
      assertNotEquals(0, gi.token);
      assertNotNull(gi.title);

      //assertNotNull(gi.simpleTags);

      assertNotEquals(0, gi.category);
      assertNotEquals(EhUtils.UNKNOWN, gi.category);
      assertNotEquals(0, gi.thumbWidth);
      assertNotEquals(0, gi.thumbHeight);
      assertNotNull(gi.thumb);
      assertNotNull(gi.posted);
      assertNotEquals(0.0, gi.rating);
      if (E_MINIMAL.equals(file) ||
          E_MINIMAL_PLUS.equals(file) ||
          E_COMPAT.equals(file) ||
          E_EXTENDED.equals(file) ||
          EX_MINIMAL.equals(file) ||
          EX_MINIMAL_PLUS.equals(file) ||
          EX_COMPAT.equals(file) ||
          EX_EXTENDED.equals(file)) {
        assertNotNull(gi.uploader);
      } else {
        assertNull(gi.uploader);
      }
      assertNotEquals(0, gi.pages);
    });
  }
}
