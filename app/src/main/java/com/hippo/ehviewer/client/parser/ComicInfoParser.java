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

package com.hippo.ehviewer.client.parser;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 ComicInfo.xml 的 Web 地址解析画廊 gid 和 token。
 */
public final class ComicInfoParser {

    public static final String FILENAME = "ComicInfo.xml";

    private static final int MAX_BYTES = 256 * 1024;

    private static final Pattern WEB = Pattern.compile(
            "<Web>\\s*([^<]+?)\\s*</Web>", Pattern.CASE_INSENSITIVE);

    private ComicInfoParser() {
    }

    /**
     * 取出第一个 Web 标签中的画廊地址。没有该标签或地址不符合画廊详情格式时返回 null。
     */
    @Nullable
    public static GalleryDetailUrlParser.Result parse(@Nullable String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        Matcher matcher = WEB.matcher(xml);
        if (!matcher.find()) {
            return null;
        }
        return GalleryDetailUrlParser.parse(matcher.group(1).trim(), true);
    }

    /**
     * 按 UTF-8 读取 ComicInfo.xml 再解析 Web。内容超过 256KB 或读取失败时返回 null。
     */
    @Nullable
    public static GalleryDetailUrlParser.Result parse(@Nullable InputStream input) {
        if (input == null) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return parse(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }
}
