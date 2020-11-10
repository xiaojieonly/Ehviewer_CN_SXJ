/*
 * Copyright 2015 Hippo Seven
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

import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class LogCat {

    private LogCat() {
    }

    public static boolean save(File file) {
        if (!FileUtils.ensureFile(file)) {
            return false;
        }

        try {
            Process p = Runtime.getRuntime().exec("logcat -d");
            IOUtils.copy(p.getInputStream(), new FileOutputStream(file));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
