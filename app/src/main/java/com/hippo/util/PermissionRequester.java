/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

public class PermissionRequester {

    /**
     * @return true for there no need to request, do your work it now.
     * false for do in {@link androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback#onRequestPermissionsResult(int, String[], int[])}
     */
    public static boolean request(final Activity activity, final String permission, String rationale, final int requestCode) {
        if (!(activity instanceof ActivityCompat.OnRequestPermissionsResultCallback)) {
            throw new IllegalStateException("The Activity must implement ActivityCompat.OnRequestPermissionsResultCallback");
        }

        if (ActivityCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            new AlertDialog.Builder(activity)
                    .setMessage(rationale)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(activity, new String[]{permission}, requestCode);
                        }
                    }).setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            return requestPermissions(activity, new String[]{permission}, requestCode);
        }

        return false;
    }

    private static boolean requestPermissions(
        final Activity activity,
        final String[] permissions,
        int requestCode
    ) {
        try {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
            return true;
        } catch (Throwable t) {
            ExceptionUtils.throwIfFatal(t);
            return false;
        }
    }
}
