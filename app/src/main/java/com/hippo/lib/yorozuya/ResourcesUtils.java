/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.yorozuya;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.AttrRes;

public final class ResourcesUtils {
    private static final Object mAccessLock = new Object();
    private static TypedValue mTmpValue = new TypedValue();

    private ResourcesUtils() {
    }

    public static float getFloat(Resources resources, int resId) {
        TypedValue outValue = new TypedValue();
        resources.getValue(resId, outValue, true);
        return outValue.getFloat();
    }

    private static void getAttrValue(Context context, int attrId, TypedValue value) {
        context.getTheme().resolveAttribute(attrId, value, true);
    }

    public static int getAttrColor(Context context, @AttrRes int attrId) {
        TypedValue value;
        synchronized (mAccessLock) {
            value = mTmpValue;
            if (value == null) {
                value = new TypedValue();
            }
            getAttrValue(context, attrId, value);
            if (value.type >= TypedValue.TYPE_FIRST_INT
                    && value.type <= TypedValue.TYPE_LAST_INT) {
                mTmpValue = value;
                return value.data;
            } else {
                throw new Resources.NotFoundException(
                        "Attribute ID #0x" + Integer.toHexString(attrId) + " type #0x"
                                + Integer.toHexString(value.type) + " is not valid");
            }
        }
    }

    public static boolean getAttrBoolean(Context context, @AttrRes int attrId) {
        synchronized (mAccessLock) {
            TypedValue value = mTmpValue;
            if (value == null) {
                mTmpValue = value = new TypedValue();
            }
            getAttrValue(context, attrId, value);
            if (value.type >= TypedValue.TYPE_FIRST_INT
                    && value.type <= TypedValue.TYPE_LAST_INT) {
                return value.data != 0;
            }
            throw new Resources.NotFoundException(
                    "Attribute ID #0x" + Integer.toHexString(attrId) + " type #0x"
                            + Integer.toHexString(value.type) + " is not valid");
        }
    }

    public static int getAttrDimensionPixelOffset(Context context, @AttrRes int attrId) {
        synchronized (mAccessLock) {
            TypedValue value = mTmpValue;
            if (value == null) {
                mTmpValue = value = new TypedValue();
            }
            getAttrValue(context, attrId, value);
            if (value.type == TypedValue.TYPE_DIMENSION) {
                return TypedValue.complexToDimensionPixelOffset(
                        value.data, context.getResources().getDisplayMetrics());
            }
            throw new Resources.NotFoundException(
                    "Attribute ID #0x" + Integer.toHexString(attrId) + " type #0x"
                            + Integer.toHexString(value.type) + " is not valid");
        }
    }
}
