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

package com.hippo.ehviewer;

import junit.framework.TestCase;

import java.net.MalformedURLException;
import java.net.URL;

public class DumpTest extends TestCase {


    public void testDump() {
        String str = "M12.1,18.55L12,18.65L11.89,18.55C7.14,14.24 4,11.39 4,8.5C4,6.5 5.5,5 7.5,5C9.04,5 10.54,6 11.07,7.36H12.93C13.46,6 14.96,5 16.5,5C18.5,5 20,6.5 20,8.5C20,11.39 16.86,14.24 12.1,18.55";
        System.out.println(str.replaceAll("\\d+(\\.\\d+)?", "12"));
    }

    public void testDumpURL() {
        String str1 = "http://exhentai.org/?f_doujinshi=1&f_manga=0&f_artistcg=0&f_gamecg=1&f_western=1&f_non-h=1&f_imageset=0&f_cosplay=0&f_asianporn=1&f_misc=1&f_search=&f_apply=Apply+Filter";
        String str2 = "http://exhentai.org/3?f_search=hentai&f_apply=Apply+Filter";
        try {
            URL url = new URL(str2);


            System.out.println(url.getHost());
            System.out.println(url.getPath());
            System.out.println(url.getQuery());


        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

}
