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

package com.hippo.ehviewer.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.R;

/**
 * 下载进度对话框
 * 用于在处理下载操作时显示加载状态，避免界面卡死
 */
public class DownloadProgressDialog {

    private final Context context;
    private AlertDialog dialog;
    private TextView messageText;
    private ProgressBar progressBar;
    private String message;

    public DownloadProgressDialog(@NonNull Context context) {
        this.context = context;
    }

    private void createDialog() {
        int padding = (int) (context.getResources().getDisplayMetrics().density * 16);

        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(padding, padding, padding, padding);
        dialogLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(1);
        progressBar.setProgress(0);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        messageText = new TextView(context);
        messageText.setText(TextUtils.isEmpty(message) ? context.getString(R.string.download_multi_select_processing) : message);
        messageText.setPadding(0, padding / 2, 0, 0);

        dialogLayout.addView(progressBar);
        dialogLayout.addView(messageText);

        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.download_dialog_title)
                .setView(dialogLayout)
                .setCancelable(false)
                .create();
    }

    public static DownloadProgressDialog show(Context context, String message) {
        DownloadProgressDialog progressDialog = new DownloadProgressDialog(context);
        progressDialog.message = message;
        progressDialog.createDialog();
        progressDialog.dialog.show();
        return progressDialog;
    }

    public void updateProgress(int current, int total) {
        if (progressBar != null) {
            progressBar.setMax(Math.max(total, 1));
            progressBar.setProgress(Math.min(current, Math.max(total, 1)));
        }
        if (messageText != null) {
            messageText.setText(context.getString(R.string.download_progress_format, current, total));
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}