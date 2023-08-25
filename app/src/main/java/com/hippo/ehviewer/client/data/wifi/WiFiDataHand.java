package com.hippo.ehviewer.client.data.wifi;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.appcenter.crashes.Crashes;

public class WiFiDataHand {
    public final static int ERROR = 0;
    public final static int RECEIVED = 1;
    public final static int SEND = 2;

    public int messageType;

    public long totalSize = 1;

    public long part = 1;

    public String errorMessage;

    public JSONObject data;

    public WiFiDataHand(int messageType) {
        this(messageType,null);
    }
    public WiFiDataHand(int messageType, JSONObject data) {
        this.messageType = messageType;
        this.data = data;
    }

    public WiFiDataHand(String msg) {
        try {
            JSONObject object = JSONObject.parseObject(msg);
            this.messageType = object.getIntValue("messageType");
            this.data = object.getJSONObject("data");
            this.totalSize = object.getLongValue("totalSize");
            this.part = object.getLongValue("part");
        } catch (Throwable throwable) {
            Crashes.trackError(throwable);
            messageType = ERROR;
            errorMessage = throwable.getMessage();
            data = new JSONObject();
        }
    }

    public JSONObject toJsonObject() {
        JSONObject object = new JSONObject();
        object.put("messageType", messageType);
        object.put("data", data);
        object.put("totalSize", totalSize);
        object.put("part", part);
        return object;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public String toSendString() {
        return toJsonObject().toString()+":END";
    }

    public byte[] getSendBytes() {
        return  toSendString().getBytes();
    }
}
