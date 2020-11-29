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

package com.hippo.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputEditText;
import com.hippo.util.ExceptionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Avoid crash on some Meizu devices
// https://github.com/android-in-china/Compatibility/issues/11
// https://stackoverflow.com/questions/51891415/nullpointerexception-on-meizu-devices-in-editor-updatecursorpositionmz/52001305
public class HackyTextInputEditText extends TextInputEditText {

  private static final boolean HAS_METHOD_UPDATE_CURSOR_POSITION_MZ;
  private static final Field FIELD_M_HINT;

  static {
    boolean hasMethodUpdateCursorPositionMz = false;
    try {
      Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("android.widget.Editor");
      Method[] methods = clazz.getDeclaredMethods();
      for (Method method : methods) {
        if ("updateCursorPositionMz".equals(method.getName())) {
          hasMethodUpdateCursorPositionMz = true;
        }
      }
    } catch (Throwable e) {
      ExceptionUtils.throwIfFatal(e);
    }

    Field fieldMHint = null;
    if (hasMethodUpdateCursorPositionMz) {
      try {
        fieldMHint = TextView.class.getDeclaredField("mHint");
        fieldMHint.setAccessible(true);
      } catch (Throwable e) {
        ExceptionUtils.throwIfFatal(e);
      }
    }

    HAS_METHOD_UPDATE_CURSOR_POSITION_MZ = hasMethodUpdateCursorPositionMz;
    FIELD_M_HINT = fieldMHint;
  }

  public HackyTextInputEditText(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public CharSequence getHint() {
    if (HAS_METHOD_UPDATE_CURSOR_POSITION_MZ && FIELD_M_HINT != null) {
      try {
        return getSuperHint();
      } catch (Throwable e) {
        ExceptionUtils.throwIfFatal(e);
        return super.getHint();
      }
    } else {
      return super.getHint();
    }
  }

  private CharSequence getSuperHint() throws IllegalAccessException {
    return (CharSequence) FIELD_M_HINT.get(this);
  }
}
