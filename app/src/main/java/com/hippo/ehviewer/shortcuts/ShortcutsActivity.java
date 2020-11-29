/*
 * Copyright 2018 Hippo Seven
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

package com.hippo.ehviewer.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.hippo.ehviewer.download.DownloadService;

/**
 * Created by onlymash on 3/25/18.
 */

public class ShortcutsActivity extends Activity{
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = null;
        Intent intent = getIntent();
        if (intent != null){
            action = intent.getAction();
            if (action != null && (action.equals(DownloadService.ACTION_START_ALL) ||
                    action.equals(DownloadService.ACTION_STOP_ALL))){
                startService(new Intent(this, DownloadService.class).setAction(action));
            }
        }
        finish();
    }
}
