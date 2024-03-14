/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.network;

import android.content.Context;
import android.content.res.Resources;
import android.util.SparseArray;

import com.hippo.ehviewer.R;

public class StatusCodeException extends Exception {

    private static final SparseArray<String> ERROR_MESSAGE_ARRAY = new SparseArray<>(24);
    private static final String DEFAULT_ERROR_MESSAGE = "Error response code";

    private final int mResponseCode;
    private final String mMessage;

    public static void initialize(Context context) {
        Resources resources = context.getApplicationContext().getResources();

        ERROR_MESSAGE_ARRAY.append(400, resources.getString(R.string.error_status_code_400));
        ERROR_MESSAGE_ARRAY.append(401, resources.getString(R.string.error_status_code_401));
        ERROR_MESSAGE_ARRAY.append(402, resources.getString(R.string.error_status_code_402));
        ERROR_MESSAGE_ARRAY.append(403, resources.getString(R.string.error_status_code_403));
        ERROR_MESSAGE_ARRAY.append(404, resources.getString(R.string.error_status_code_404));
        ERROR_MESSAGE_ARRAY.append(405, resources.getString(R.string.error_status_code_405));
        ERROR_MESSAGE_ARRAY.append(406, resources.getString(R.string.error_status_code_406));
        ERROR_MESSAGE_ARRAY.append(407, resources.getString(R.string.error_status_code_407));
        ERROR_MESSAGE_ARRAY.append(408, resources.getString(R.string.error_status_code_408));
        ERROR_MESSAGE_ARRAY.append(409, resources.getString(R.string.error_status_code_409));
        ERROR_MESSAGE_ARRAY.append(410, resources.getString(R.string.error_status_code_410));
        ERROR_MESSAGE_ARRAY.append(411, resources.getString(R.string.error_status_code_411));
        ERROR_MESSAGE_ARRAY.append(412, resources.getString(R.string.error_status_code_412));
        ERROR_MESSAGE_ARRAY.append(413, resources.getString(R.string.error_status_code_413));
        ERROR_MESSAGE_ARRAY.append(414, resources.getString(R.string.error_status_code_414));
        ERROR_MESSAGE_ARRAY.append(415, resources.getString(R.string.error_status_code_415));
        ERROR_MESSAGE_ARRAY.append(416, resources.getString(R.string.error_status_code_416));
        ERROR_MESSAGE_ARRAY.append(417, resources.getString(R.string.error_status_code_417));

        ERROR_MESSAGE_ARRAY.append(500, resources.getString(R.string.error_status_code_500));
        ERROR_MESSAGE_ARRAY.append(501, resources.getString(R.string.error_status_code_501));
        ERROR_MESSAGE_ARRAY.append(502, resources.getString(R.string.error_status_code_502));
        ERROR_MESSAGE_ARRAY.append(503, resources.getString(R.string.error_status_code_503));
        ERROR_MESSAGE_ARRAY.append(504, resources.getString(R.string.error_status_code_504));
        ERROR_MESSAGE_ARRAY.append(505, resources.getString(R.string.error_status_code_505));
    }

    public StatusCodeException(int responseCode) {
        mResponseCode = responseCode;
        mMessage = ERROR_MESSAGE_ARRAY.get(responseCode, DEFAULT_ERROR_MESSAGE);
    }

    public StatusCodeException(int responseCode, String message) {
        mResponseCode = responseCode;
        mMessage = message;
    }

    public int getResponseCode() {
        return mResponseCode;
    }

    public boolean isIdentifiedResponseCode() {
        return !DEFAULT_ERROR_MESSAGE.equals(mMessage);
    }

    @Override
    public String getMessage() {
        return mMessage;
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }
}
