package com.hippo.anotherviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class SiteNewsDetail implements Parcelable {
    public String webData;
    private String eventPane;
    private String htmlData;

    public SiteNewsDetail() {
        eventPane = null;
        this.webData = null;
        this.htmlData = null;
    }

    public SiteNewsDetail(String webData) {
        eventPane = null;
        this.webData = webData;
    }

    protected SiteNewsDetail(Parcel in) {
        this.webData = in.readString();
        this.eventPane = in.readString();
        this.htmlData = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.webData);
        dest.writeString(this.eventPane);
        dest.writeString(this.htmlData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SiteNewsDetail> CREATOR = new Creator<SiteNewsDetail>() {
        @Override
        public SiteNewsDetail createFromParcel(Parcel in) {
            return new SiteNewsDetail(in);
        }

        @Override
        public SiteNewsDetail[] newArray(int size) {
            return new SiteNewsDetail[size];
        }
    };

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
