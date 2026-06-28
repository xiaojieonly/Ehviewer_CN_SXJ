package com.hippo.ehviewer.client.data;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class EhNewsDetail {
    public String webData;
    private String eventPane;
    private String htmlData;

    public EhNewsDetail() {
        eventPane = null;
        this.webData = null;
        this.htmlData = null;
    }

    public EhNewsDetail(String webData) {
        eventPane = null;
        this.webData = webData;
    }

    public String getEventPane() {
        if (eventPane != null) {
            return eventPane;
        }
        Document document = Jsoup.parse(webData);
        Element eventPaneElement = document.getElementById("eventpane");
        if (eventPaneElement != null && eventPaneElement.childrenSize() == 3) {
            eventPane = eventPaneElement.html();
        }
        return eventPane;
    }

    public String getHtmlData() {
        Document document = Jsoup.parse(webData);
        Element element = document.child(0);
        return element.outerHtml();
    }

}
