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
package com.hippo.scene

import android.os.Bundle

class Announcer(var clazz: Class<*>) {
    @JvmField
    var args: Bundle? = null
    @JvmField
    var tranHelper: TransitionHelper? = null
    @JvmField
    var requestFrom: SceneFragment? = null
    @JvmField
    var requestCode = 0
    fun setArgs(args: Bundle?): Announcer {
        this.args = args
        return this
    }

    fun setTranHelper(tranHelper: TransitionHelper?): Announcer {
        this.tranHelper = tranHelper
        return this
    }

    fun setRequestCode(requestFrom: SceneFragment?, requestCode: Int): Announcer {
        this.requestFrom = requestFrom
        this.requestCode = requestCode
        return this
    }
}