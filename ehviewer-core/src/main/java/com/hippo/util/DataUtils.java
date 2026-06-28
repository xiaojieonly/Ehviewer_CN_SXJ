package com.hippo.util;

public class DataUtils {
    public static byte[] decodeBase64(String str) {
        return java.util.Base64.getDecoder().decode(str);
    }

    public static String encodeBase64(byte[] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }

    public static <T> T copy(T obj) {
        return obj;
    }
}
