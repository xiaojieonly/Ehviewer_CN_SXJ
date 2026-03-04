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
package com.hippo.util

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object JsoupUtils {
    @JvmStatic
    fun getElementByClass(doc: Document, className: String): Element? {
        val elements = doc.getElementsByClass(className)
        if (elements != null && elements.isNotEmpty()) {
            return elements[0]
        } else {
            return null
        }
    }

    @JvmStatic
    fun getElementByClass(element: Element, className: String): Element? {
        val elements = element.getElementsByClass(className)
        return if (elements != null && elements.isNotEmpty()) {
            elements[0]
        } else {
            null
        }
    }

    @JvmStatic
    fun getElementsByClass(element: Element, className: String): Elements? {
        val elements = element.getElementsByClass(className)
        return if (elements != null && elements.isNotEmpty()) {
            elements
        } else {
            null
        }
    }

    @JvmStatic
    fun getElementByTag(element: Element, tagName: String): Element? {
        val elements = element.getElementsByTag(tagName)
        return if (elements != null && elements.isNotEmpty()) {
            elements[0]
        } else {
            null
        }
    }
}
