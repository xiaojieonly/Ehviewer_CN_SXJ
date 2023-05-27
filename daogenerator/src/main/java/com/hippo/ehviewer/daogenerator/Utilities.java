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
package com.hippo.ehviewer.daogenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class Utilities {

    public static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }

    private static String fileReader(String path) {
        String reader = "";
        //        File file = new File("daogenerator/src/main/java/com/hippo/ehviewer/daogenerator/database/artist.txt");
        File file = new File(path);
        String encoding = System.getProperty("file.encoding");
        try {
            if (file.isFile() && file.exists()) {
                InputStreamReader textReader = new InputStreamReader(new FileInputStream(file), encoding);
                BufferedReader bufferedReader = new BufferedReader(textReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    reader = reader + line;
                }
            } else {
                System.out.println("路径有误");
            }
        } catch (IOException e) {
            System.err.println("????????");
        }
        return reader;
    }
}
