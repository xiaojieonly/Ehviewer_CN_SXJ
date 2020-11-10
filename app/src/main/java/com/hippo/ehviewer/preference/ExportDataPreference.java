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
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.GetText;
import com.hippo.ehviewer.R;
import com.hippo.util.ReadableTime;
import java.io.File;

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
      File dir = AppConfig.getExternalDataDir();
      if (dir != null) {
        File file = new File(dir, ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".db");
        if (EhDB.exportDB(getApplication(), file)) {
          return file;
        }
      }
      return null;
    }

    @Override
    protected void onPostExecute(Object o) {
      Toast.makeText(getApplication(),
          (o instanceof File)
              ? GetText.getString(R.string.settings_advanced_export_data_to, ((File) o).getPath())
              : GetText.getString(R.string.settings_advanced_export_data_failed),
          Toast.LENGTH_SHORT).show();
      super.onPostExecute(o);
    }
  }
}
