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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.R;

/**
 * 下载进度对话框
 * 用于在处理下载操作时显示加载状态，避免界面卡死
 */
public class DownloadProgressDialog extends Dialog {

    private TextView messageText;
    private ProgressBar progressBar;
    private String message;

    public DownloadProgressDialog(@NonNull Context context) {
        super(context);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_download_progress, null);
        setContentView(view);
        
        messageText = view.findViewById(R.id.message_text);
        progressBar = view.findViewById(R.id.progress_bar);
        
        if (message != null) {
            messageText.setText(message);
        }
    }

    /**
     * 设置进度消息
     * @param message 要显示的消息
     */
    public void setMessage(String message) {
        this.message = message;
        if (messageText != null) {
            messageText.setText(message);
        }
    }

    /**
     * 更新进度信息
     * @param current 当前进度
     * @param total 总数
     */
    public void updateProgress(int current, int total) {
        if (messageText != null) {
            String progressText = getContext().getString(R.string.download_progress_format, current, total);
            messageText.setText(progressText);
        }
    }

    /**
     * 创建并显示下载进度对话框
     * @param context 上下文
     * @param message 初始消息
     * @return 对话框实例
     */
    public static DownloadProgressDialog show(Context context, String message) {
        DownloadProgressDialog dialog = new DownloadProgressDialog(context);
        dialog.setMessage(message);
        dialog.show();
        return dialog;
    }
}