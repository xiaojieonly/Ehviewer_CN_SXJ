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

package com.hippo.ehviewer;

/*
 * Created by Hippo on 2018/3/22.
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.Pair;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class HostsTest {

  @Test
  public void testIsValidHost() {
    assertTrue(Hosts.isValidHost("ni.hao"));
    assertFalse(Hosts.isValidHost("ni.ha~o"));
    assertFalse(Hosts.isValidHost(".ni.hao"));
    assertFalse(Hosts.isValidHost("ni.hao."));
    assertFalse(Hosts.isValidHost("ni..hao."));
    assertFalse(Hosts.isValidHost("."));
    assertFalse(Hosts.isValidHost(""));
    assertFalse(Hosts.isValidHost(null));
  }

  @Test
  public void testIsValidIp() {
    assertTrue(Hosts.isValidIp("127.0.0.1"));
    assertTrue(Hosts.isValidIp("0.0.0.0"));
    assertFalse(Hosts.isValidIp("324.0.0.1"));
    assertFalse(Hosts.isValidIp("127.0.0."));
    assertFalse(Hosts.isValidIp("127.0.0"));
    assertFalse(Hosts.isValidIp("-1.0.0"));
    assertFalse(Hosts.isValidIp(""));
    assertFalse(Hosts.isValidIp(null));
  }

  @Test
  public void testGet() {
    Hosts hosts = new Hosts(RuntimeEnvironment.application, "hosts.db");

    assertEquals(null, hosts.get("ni.hao"));
    hosts.put("ni.hao", "127.0.0.1");
    assertEquals("ni.hao/127.0.0.1", hosts.get("ni.hao").toString());

    assertEquals(null, hosts.get(null));
  }

  @Test
  public void testPut() {
    Hosts hosts = new Hosts(RuntimeEnvironment.application, "hosts.db");

    assertEquals(null, hosts.get("ni.hao"));
    assertEquals(true, hosts.put("ni.hao", "127.0.0.1"));
    assertEquals("ni.hao/127.0.0.1", hosts.get("ni.hao").toString());
    assertEquals(true, hosts.put("ni.hao", "127.0.0.2"));
    assertEquals("ni.hao/127.0.0.2", hosts.get("ni.hao").toString());

    assertEquals(false, hosts.put(".wo.hao", "127.0.0.1"));
    assertEquals(null, hosts.get(".wo.hao"));

    assertEquals(false, hosts.put("wo.hao", ".127.0.0.1"));
    assertEquals(null, hosts.get("wo.hao"));

    assertEquals(false, hosts.put(null, "127.0.0.1"));
    assertEquals(false, hosts.put("wo.hao", null));
    assertEquals(false, hosts.put(null, null));
    assertEquals(null, hosts.get("wo.hao"));
  }

  @Test
  public void testDelete() {
    Hosts hosts = new Hosts(RuntimeEnvironment.application, "hosts.db");

    hosts.put("ni.hao", "127.0.0.1");
    assertEquals("ni.hao/127.0.0.1", hosts.get("ni.hao").toString());
    hosts.put("ni.hao", "127.0.0.2");
    hosts.delete("ni.hao");
    assertEquals(null, hosts.get("ni.hao"));

    hosts.delete(null);
  }

  @Test
  public void testGetAll() {
    Hosts hosts = new Hosts(RuntimeEnvironment.application, "hosts.db");

    List<Pair<String, String>> all = hosts.getAll();
    assertEquals(0, all.size());

    hosts.put("ni.hao", "127.0.0.1");
    hosts.put("wo.hao", "127.0.0.2");

    all = hosts.getAll();
    assertEquals(2, all.size());

    assertEquals("ni.hao", all.get(0).first);
    assertEquals("127.0.0.1", all.get(0).second);
    assertEquals("wo.hao", all.get(1).first);
    assertEquals("127.0.0.2", all.get(1).second);
  }
}
