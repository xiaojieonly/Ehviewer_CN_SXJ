package com.hippo.ehviewer.util;

import com.hippo.ehviewer.Settings;
import com.microsoft.appcenter.analytics.Analytics;

import java.util.Map;

public class AppCenterAnalytics {

    private static boolean isEnable(){
        return Settings.getEnableAnalytics();
    }

    public static void trackEvent(String name){
        if (isEnable()){
            Analytics.trackEvent(name);
        }
    }

    public static void trackEvent(String name, Map<String, String> properties){
        if (isEnable()){
            Analytics.trackEvent(name,properties);
        }
    }

    public static void trackEvent(String name,Map<String, String> properties, int flags){
        if (isEnable()){
            Analytics.trackEvent(name,properties,flags);
        }
    }

}
