package com.hippo.network;

public class StatusCodeException extends Exception {
    public int code;

    public StatusCodeException(int code) {
        super("HTTP " + code);
        this.code = code;
    }
}
