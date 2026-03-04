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

package com.hippo.util;
/*
 * Created by Hippo on 2017/9/4.
 */

public class HashCodeUtils {

  private static final int X = 31;

  /**
   * <pre>{@code
   * args[0].hashCode()*31^(n-1) + args[1].hashCode()*31^(n-2) + ... + args[n-1].hashCode()
   * }</pre>
   * Returns 0 if {@code args == null}.
   */
  public static int hashCode(Object... args) {
    if (args == null) {
      return 0;
    }
    int hash = 0;
    for (Object o: args) {
      hash = X * hash + (o != null ? o.hashCode() : 0);
    }
    return hash;
  }
}
