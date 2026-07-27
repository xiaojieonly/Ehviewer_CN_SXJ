package com.hippo.ehviewer.client;

public class EhClient {
    public interface Task {
        boolean isCancelled();
        void setCall(okhttp3.Call call);
    }
}
