package com.hippo.ehviewer.client.data.wifi;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class WiFiDataHand {
    public final static int ERROR = 0;
    public final static int RECEIVED = 1;
    public final static int SEND = 2;

    public int messageType;

    public int dataType;

    public long pageSize = 1;

    public long pageIndex = 1;

    public String errorMessage;

    private JSONObject data;

    public WiFiDataHand(int messageType) {
        this(messageType, null);
    }

    public WiFiDataHand(int messageType, JSONObject data) {
        this.messageType = messageType;
        this.data = data;
    }

    public WiFiDataHand(String msg) {
        try {
            JSONObject object = JSONObject.parseObject(msg);
            this.messageType = object.getIntValue("messageType");
            this.dataType = object.getIntValue("dataType");
            this.data = object.getJSONObject("data");
            this.pageSize = object.getLongValue("totalSize");
            this.pageIndex = object.getLongValue("part");
        } catch (Throwable throwable) {
            FirebaseCrashlytics.getInstance().recordException(throwable);
            messageType = ERROR;
            errorMessage = throwable.getMessage();
            data = new JSONObject();
        }
    }

    public JSONObject getData() {
        return data;
    }

    public void addData(String key, Object object) {
        if (data == null) {
            data = new JSONObject();
        }
        data.put(key, object);
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public JSONObject toJsonObject() {
        JSONObject object = new JSONObject();
        object.put("messageType", messageType);
        object.put("dataType", dataType);
        object.put("data", data);
        object.put("totalSize", pageSize);
        object.put("part", pageIndex);
        return object;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public String toSendString() {
        return toJsonObject().toString() + ":END";
    }

    public byte[] getSendBytes() {
        return toSendString().getBytes();
    }
}
