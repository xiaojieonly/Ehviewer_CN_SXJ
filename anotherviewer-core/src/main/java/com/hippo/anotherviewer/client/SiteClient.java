package com.hippo.anotherviewer.client;

public class SiteClient {
    public interface Task {
        boolean isCancelled();
        void setCall(okhttp3.Call call);
    }
}
