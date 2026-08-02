package com.hippo.anotherviewer;

public class SiteApplication {
    private static SiteApplication instance;

    public static SiteApplication getInstance() {
        if (instance == null) instance = new SiteApplication();
        return instance;
    }

    public void showEventPane(String html) {}
}
