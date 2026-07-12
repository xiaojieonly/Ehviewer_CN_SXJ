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
import androidx.annotation.NonNull;

public class ExportDataPreference extends TaskPreference {

  public ExportDataPreference(Context context) {
    super(context);
  }

  public ExportDataPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ExportDataPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @NonNull
  @Override
  protected Task onCreateTask() {
    return new ExportDataTask(getContext());
  }

  private static class ExportDataTask extends Task {

    public ExportDataTask(@NonNull Context context) {
      super(context);
    }

    @Override
    protected Object doInBackground(Void... voids) {
      DataBackupService.startExport(getApplication());
      return Boolean.TRUE;
    }

    @Override
    protected void onPostExecute(Object o) {
      super.onPostExecute(o);
    }
  }
}
