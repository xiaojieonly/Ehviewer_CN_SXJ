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

import android.util.Base64;
import android.util.Pair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

  @Test
  public void locationNamespaceMapsToLocPrefix() {
    assertEquals("loc:", EhTagDatabase.namespaceToPrefix("location"));
    assertEquals("location", EhTagDatabase.prefixToNamespace("loc:"));
  }

  @Test
  public void locPrefixLooksUpAndSuggestsFullNamespace() throws IOException {
    String encoded = Base64.encodeToString("沙滩".getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    byte[] line = ("loc:beach\r" + encoded + "\n").getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(out);
    dos.writeInt(line.length);
    dos.write(line);

    EhTagDatabase db;
    try (BufferedSource source = Okio.buffer(Okio.source(new ByteArrayInputStream(out.toByteArray())))) {
      db = new EhTagDatabase("location-test", source);
    }

    assertEquals("沙滩", db.getTranslation("loc:beach"));

    List<Pair<String, String>> hints = db.suggest("location:beach");
    assertEquals(1, hints.size());
    assertEquals("沙滩", hints.get(0).first);
    assertEquals("location:beach", hints.get(0).second);
  }
}
