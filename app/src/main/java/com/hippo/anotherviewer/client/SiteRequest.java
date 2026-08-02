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

package com.hippo.anotherviewer.client;

import com.hippo.anotherviewer.Settings;

public class SiteRequest {

    private int mMethod;
    private Object[] mArgs;
    private SiteClient.Callback mCallback;
    private SiteConfig mSiteConfig;

    SiteClient.Task task;

    private boolean mCancel = false;

    public SiteRequest setMethod(int method) {
        mMethod = method;
        return this;
    }

    public SiteRequest setArgs(Object... args) {
        mArgs = args;
        return this;
    }

    public SiteRequest setCallback(SiteClient.Callback callback) {
        mCallback = callback;
        return this;
    }

    public SiteRequest setSiteConfig(SiteConfig ehConfig) {
        mSiteConfig = ehConfig;
        return this;
    }

    public int getMethod() {
        return mMethod;
    }

    public Object[] getArgs() {
        return mArgs;
    }

    public SiteClient.Callback getCallback() {
        return mCallback;
    }

    public SiteConfig getSiteConfig() {
        return mSiteConfig != null ? mSiteConfig : Settings.getSiteConfig();
    }

    public void cancel() {
        if (!mCancel) {
            mCancel = true;
            if (task != null) {
                task.stop();
                task = null;
            }
        }
    }

    public boolean isCancelled() {
        return mCancel;
    }
}
