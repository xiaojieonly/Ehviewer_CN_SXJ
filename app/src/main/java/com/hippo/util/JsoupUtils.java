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

package com.hippo.util;

import androidx.annotation.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class JsoupUtils {

    @Nullable
    public static Element getElementByClass(Document doc, String className) {
        Elements elements = doc.getElementsByClass(className);
        if (elements != null && elements.size() > 0) {
            return elements.get(0);
        } else {
            return null;
        }
    }

    @Nullable
    public static Element getElementByClass(Element element, String className) {
        Elements elements = element.getElementsByClass(className);
        if (elements != null && elements.size() > 0) {
            return elements.get(0);
        } else {
            return null;
        }
    }

    @Nullable
    public static Element getElementByTag(Element element, String tagName) {
        Elements elements = element.getElementsByTag(tagName);
        if (elements != null && elements.size() > 0) {
            return elements.get(0);
        } else {
            return null;
        }
    }
}
