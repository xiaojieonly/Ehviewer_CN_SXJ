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

package com.hippo.ehviewer.ui.fragment;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.DownloadedFileManager;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 扫描下载文件任务
 */
public class ScanDownloadFilesTask extends AsyncTask<Void, Integer, Boolean> {

    private static final String TAG = ScanDownloadFilesTask.class.getSimpleName();

    @Nullable
    private final DownloadFragment mFragment;
    @NonNull
    private final Context mContext;
    private ProgressDialog mProgressDialog;
    private Handler mHandler;
    private String mError;

    public ScanDownloadFilesTask(@Nullable DownloadFragment fragment) {
        mFragment = fragment;
        mContext = fragment != null ? fragment.requireContext() : EhApplication.getInstance();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onPreExecute() {
        // 显示进度对话框
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.settings_download_scan_download_files);
        mProgressDialog.setMessage(mContext.getString(R.string.scan_download_files_scanning));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Starting scan download files task using BackgroundTaskManager");
        try {
            BackgroundTaskManager taskManager = BackgroundTaskManager.getInstance();
            Log.i(TAG, "BackgroundTaskManager instance obtained");
            
            // 创建进度监听器
            DownloadedFileManager.ScanProgressListener listener = new DownloadedFileManager.ScanProgressListener() {
                @Override
                public void onProgress(int current, int total) {
                    Log.d(TAG, "Scan progress: " + current + "/" + total);
                    publishProgress(current, total);
                }

                @Override
                public void onCompleted() {
                    Log.i(TAG, "Scan completed successfully");
                    // 在onPostExecute中处理
                }

                @Override
                public void onError(Exception e) {
                    mError = e.getMessage();
                    Log.e(TAG, "Error scanning download files", e);
                }
            };
            
            // 提交任务并等待完成
            Log.i(TAG, "Submitting scan download task to BackgroundTaskManager");
            Future<?> future = taskManager.submitScanDownloadTask(listener);
            
            // 等待任务完成，同时响应取消请求
            try {
                future.get(); // 等待任务完成
                Log.i(TAG, "Scan task completed successfully");
                
                // 检查扫描状态
                DownloadedFileManager manager = DownloadedFileManager.getInstance();
                int finalStatus = manager.getScanStatus();
                Log.i(TAG, "Scan finished with status: " + finalStatus + 
                      " (SCAN_STATUS_COMPLETED = " + DownloadedFileManager.SCAN_STATUS_COMPLETED + ")");
                
                if (finalStatus == DownloadedFileManager.SCAN_STATUS_ERROR) {
                    String error = manager.getScanError();
                    Log.e(TAG, "Scan failed with error: " + error);
                    if (error != null) {
                        mError = error;
                    }
                }
                
                return finalStatus == DownloadedFileManager.SCAN_STATUS_COMPLETED;
                
            } catch (CancellationException e) {
                Log.i(TAG, "Scan task was cancelled");
                return false;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                mError = cause != null ? cause.getMessage() : e.getMessage();
                Log.e(TAG, "Error in scan task execution", e);
                return false;
            } catch (InterruptedException e) {
                Log.i(TAG, "Scan task was interrupted");
                Thread.currentThread().interrupt();
                return false;
            }
            
        } catch (Exception e) {
            mError = e.getMessage();
            Log.e(TAG, "Error in ScanDownloadFilesTask", e);
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (mProgressDialog != null && values.length >= 2) {
            int current = values[0];
            int total = values[1];
            mProgressDialog.setMax(total);
            mProgressDialog.setProgress(current);
            mProgressDialog.setMessage(mContext.getString(R.string.scan_download_files_scanning_progress, current, total));
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        // 关闭进度对话框
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        // 显示结果
        mHandler.post(() -> {
            if (success) {
                Toast.makeText(mContext, R.string.scan_download_files_completed, Toast.LENGTH_SHORT).show();
            } else {
                String message = mError != null ? mError : mContext.getString(R.string.scan_download_files_failed);
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.scan_download_files_failed_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    @Override
    protected void onCancelled() {
        // 关闭进度对话框
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}