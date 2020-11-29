/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.preference;

import android.preference.Preference;
import android.preference.PreferenceManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PreferenceUtils {
    private PreferenceUtils() {}

    private static final Method mRegisterOnActivityDestroyListener;
    private static final Method mUnregisterOnActivityDestroyListener;

    static {
        Method method;
        Class<?> clazz = PreferenceManager.class;

        method = null;
        try {
            method = clazz.getDeclaredMethod("registerOnActivityDestroyListener",
                    PreferenceManager.OnActivityDestroyListener.class);
            if (null != method) {
                method.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        mRegisterOnActivityDestroyListener = method;

        method = null;
        try {
            method = clazz.getDeclaredMethod("unregisterOnActivityDestroyListener",
                    PreferenceManager.OnActivityDestroyListener.class);
            if (null != method) {
                method.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        mUnregisterOnActivityDestroyListener = method;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    public static void registerOnActivityDestroyListener(Preference preference,
            PreferenceManager.OnActivityDestroyListener listener) {
        if (null == mRegisterOnActivityDestroyListener || null == preference || null == listener) {
            return;
        }
        PreferenceManager preferenceManager = preference.getPreferenceManager();
        if (null == preferenceManager) {
            return;
        }

        try {
            mRegisterOnActivityDestroyListener.invoke(preferenceManager, listener);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    public static void unregisterOnActivityDestroyListener(Preference preference,
            PreferenceManager.OnActivityDestroyListener listener) {
        if (null == mUnregisterOnActivityDestroyListener || null == preference || null == listener) {
            return;
        }
        PreferenceManager preferenceManager = preference.getPreferenceManager();
        if (null == preferenceManager) {
            return;
        }

        try {
            mUnregisterOnActivityDestroyListener.invoke(preferenceManager, listener);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
