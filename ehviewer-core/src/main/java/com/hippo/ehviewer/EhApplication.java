package com.hippo.ehviewer;

public class EhApplication {
    private static EhApplication instance;

    public static EhApplication getInstance() {
        if (instance == null) instance = new EhApplication();
        return instance;
    }

    public void showEventPane(String html) {}
}
