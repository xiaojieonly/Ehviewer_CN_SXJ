/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.upload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * JVM 单测 for {@link UploadResume#missingPages(int, java.util.List)}：
 * 下载推送续传的缺失页计算（fresh / 部分已有 / 乱序重复越界 / 空 total）。
 */
public class UploadResumeTest {

    @Test
    public void freshUpload_returnsAllPages() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), UploadResume.missingPages(5, null));
        assertEquals(Arrays.asList(1, 2, 3), UploadResume.missingPages(3, Collections.<Integer>emptyList()));
    }

    @Test
    public void skipsExistingPages() {
        assertEquals(Arrays.asList(2, 4), UploadResume.missingPages(4, Arrays.asList(1, 3)));
    }

    @Test
    public void ignoresOutOfRangeAndDuplicates() {
        // 越界（0/4/-2）与重复（1 出现两次）都被忽略；1 在范围内应被跳过。
        assertEquals(Arrays.asList(2, 3), UploadResume.missingPages(3, Arrays.asList(0, 4, 1, 1, -2)));
        // 只有越界值时等价于空表：全部页都缺失。
        assertEquals(Arrays.asList(1, 2, 3), UploadResume.missingPages(3, Arrays.asList(0, 4, -2)));
    }

    @Test
    public void zeroOrNegativeTotal_returnsEmpty() {
        assertEquals(Collections.<Integer>emptyList(), UploadResume.missingPages(0, null));
        assertEquals(Collections.<Integer>emptyList(), UploadResume.missingPages(-1, Arrays.asList(1)));
    }
}
