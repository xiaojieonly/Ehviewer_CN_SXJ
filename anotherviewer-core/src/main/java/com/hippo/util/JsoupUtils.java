package com.hippo.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JsoupUtils {
    public static Element getElementByClass(Element element, String className) {
        return element.getElementsByClass(className).first();
    }

    public static Elements getElementsByClass(Element element, String className) {
        return element.getElementsByClass(className);
    }

    public static Element getElementByTag(Element element, String tagName) {
        return element.getElementsByTag(tagName).first();
    }
}
