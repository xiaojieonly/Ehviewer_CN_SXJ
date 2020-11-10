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

package com.hippo.scene;

import android.os.Bundle;

public final class Announcer {

    Class<?> clazz;
    Bundle args;
    TransitionHelper tranHelper;
    SceneFragment requestFrom;
    int requestCode;

    public Announcer(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Announcer setArgs(Bundle args) {
        this.args = args;
        return this;
    }

    public Bundle getArgs() {
        return args;
    }

    public Announcer setTranHelper(TransitionHelper tranHelper) {
        this.tranHelper = tranHelper;
        return this;
    }

    public Announcer setRequestCode(SceneFragment requestFrom, int requestCode) {
        this.requestFrom = requestFrom;
        this.requestCode = requestCode;
        return this;
    }
}
