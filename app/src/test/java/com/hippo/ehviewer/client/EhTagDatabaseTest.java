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

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
public class EhTagDatabaseTest {

  @Test
  public void readTheList() throws IOException {
    InputStream resource = EhTagDatabaseTest.class.getResourceAsStream("EhTagDatabaseTest");

    EhTagDatabase db;
    try (BufferedSource source = Okio.buffer(Okio.source(resource))) {
      db = new EhTagDatabase("EhTagDatabaseTest", source);
    }

    assertEquals("a", db.getTranslation("1"));
    assertEquals("ab", db.getTranslation("12"));
    assertEquals("abc", db.getTranslation("123"));
    assertEquals("abcd", db.getTranslation("1234"));
    assertEquals("1", db.getTranslation("a"));
    assertEquals("12", db.getTranslation("ab"));
    assertEquals("123", db.getTranslation("abc"));
    assertEquals("1234", db.getTranslation("abcd"));
    assertNull(db.getTranslation("21"));
  }
}
