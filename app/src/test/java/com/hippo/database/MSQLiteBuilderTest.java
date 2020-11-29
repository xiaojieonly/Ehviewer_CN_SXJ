/*
 * Copyright 2017 Hippo Seven
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

package com.hippo.database;

/*
 * Created by Hippo on 2017/9/4.
 */

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class MSQLiteBuilderTest {

  @Test
  public void testGetStatements() {
    MSQLiteBuilder builder = new MSQLiteBuilder()
        .version(1)
        .statement("11")
        .statement("12")
        .version(3)
        .statement("31")
        .statement("32");

    List<String> list = new ArrayList<>();
    list.add("31");
    list.add("32");
    assertEquals(list, builder.getStatements(1, 3));
  }
}
