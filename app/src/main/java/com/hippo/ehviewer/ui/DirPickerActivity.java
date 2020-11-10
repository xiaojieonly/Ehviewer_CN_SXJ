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

package com.hippo.ehviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.R;
import com.hippo.ripple.Ripple;
import com.hippo.widget.DirExplorer;
import com.hippo.yorozuya.FileUtils;
import java.io.File;

public class DirPickerActivity extends ToolbarActivity
        implements View.OnClickListener, DirExplorer.OnChangeDirListener {

    public static final String KEY_FILE_URI = "file_uri";

    public static final String KEY_FILE_PATH = "file_path";

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private TextView mPath;
    @Nullable
    private DirExplorer mDirExplorer;
    private View mDefault;
    @Nullable
    private View mOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dir_picker);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        mPath = findViewById(R.id.path);
        mDirExplorer = findViewById(R.id.dir_explorer);
        mDefault = findViewById(R.id.preset);
        mOk = findViewById(R.id.ok);

        File file;
        if (null == savedInstanceState) {
            file = onInit();
        } else {
            file = onRestore(savedInstanceState);
        }

        mDirExplorer.setCurrentFile(file);
        mDirExplorer.setOnChangeDirListener(this);

        Ripple.addRipple(mOk, !AttrResources.getAttrBoolean(this, R.attr.isLightTheme));

        mDefault.setOnClickListener(this);
        mOk.setOnClickListener(this);

        mPath.setText(mDirExplorer.getCurrentFile().getPath());
    }

    private File onInit() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri fileUri = intent.getParcelableExtra(KEY_FILE_URI);
            if (fileUri != null) {
                return new File(fileUri.getPath());
            }
        }
        return null;
    }

    private File onRestore(@NonNull Bundle savedInstanceState) {
        String filePath = savedInstanceState.getString(KEY_FILE_PATH);
        if (null != filePath) {
            return new File(filePath);
        } else {
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != mDirExplorer) {
            outState.putString(KEY_FILE_PATH, mDirExplorer.getCurrentFile().getPath());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mPath = null;
        mDirExplorer = null;
        mOk = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(@NonNull View v) {
        if (mDefault == v) {
            File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(this, null);
            File[] dirs = new File[externalFilesDirs.length + 1];
            dirs[0] = AppConfig.getDefaultDownloadDir();
            for (int i = 0; i < externalFilesDirs.length; i++) {
                dirs[i + 1] = new File(externalFilesDirs[i], "download");
            }

            CharSequence[] items = new CharSequence[dirs.length];
            items[0] = getString(R.string.default_directory);
            for (int i = 1; i < items.length; i++) {
                items[i] = getString(R.string.application_file_directory, i);
            }

            new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
                File dir = dirs[which];
                if (!FileUtils.ensureDirectory(dir)) {
                    Toast.makeText(DirPickerActivity.this, R.string.directory_not_writable, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mDirExplorer != null) {
                    mDirExplorer.setCurrentFile(dir);
                }
            }).show();
        } else if (mOk == v) {
            if (null == mDirExplorer) {
                return;
            }
            File file = mDirExplorer.getCurrentFile();
            if (!file.canWrite()) {
                Toast.makeText(this, R.string.directory_not_writable, Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent();
                intent.setData(Uri.fromFile(file));
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    @Override
    public void onChangeDir(File dir) {
        if (null != mPath) {
            mPath.setText(dir.getPath());
        }
    }
}
