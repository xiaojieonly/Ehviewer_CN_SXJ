/*
 * Copyright 2019 Hippo Seven
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

package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.R;

/**
 * 导出数据偏好设置
 * 已改造为使用统一的后台任务管理器
 */
public class ExportDataPreference extends Preference {

  public ExportDataPreference(Context context) {
    super(context);
  }

  public ExportDataPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ExportDataPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onClick() {
    Context context = getContext();
    com.hippo.ehviewer.task.ExportDataTask task = new com.hippo.ehviewer.task.ExportDataTask(context);
    BackgroundTaskManager.getInstance().submitBackgroundTask(task);
    Toast.makeText(context, R.string.settings_advanced_export_data_started, Toast.LENGTH_SHORT).show();
  }
}
