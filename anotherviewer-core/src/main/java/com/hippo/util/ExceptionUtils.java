package com.hippo.util;

public class ExceptionUtils {
    public static void throwIfFatal(Throwable t) {
        if (t instanceof Error) {
            throw (Error) t;
        }
    }

    public static String getStackTraceString(Throwable t) {
        if (t == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
