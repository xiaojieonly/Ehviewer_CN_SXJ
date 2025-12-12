package com.hippo.ehviewer.ui.fragment;

import static com.hippo.ehviewer.GetText.getString;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryVersionMap;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MergeDuplicateGalleryTask extends AsyncTask<Void, Object, Boolean> {

    private static final String TAG = "MergeDuplicateGalleryTask";
    
    public static final int STEP_SCAN = 0;
    public static final int STEP_ANALYZE = 1;
    public static final int STEP_MERGE = 2;
    public static final int STEP_BACKUP = 3;
    
    public static final int RESPONSE_YES = 0;
    public static final int RESPONSE_YES_TO_ALL = 1;
    public static final int RESPONSE_NO = 2;
    public static final int RESPONSE_NO_TO_ALL = 3;
    
    private final WeakReference<DownloadFragment> mFragment;
    private ProgressDialog mProgressDialog;
    private Context mContext;
    
    // 扫描结果
    private List<GalleryGroup> mGalleryGroups = new ArrayList<>();
    private int mTotalGalleries = 0;
    private int mCurrentGallery = 0;
    
    // 用户选择
    private boolean mMergeAll = false;
    private boolean mSkipAll = false;
    private int mUserResponse = -1;
    private CountDownLatch mUserResponseLatch = new CountDownLatch(1);
    
    // 错误日志
    private StringBuilder mErrorLog = new StringBuilder();
    private String mLastError = "";
    
    // 合并日志
    private StringBuilder mMergeLog = new StringBuilder();
    private int mMergedCount = 0;
    private int mSkippedCount = 0;
    
    public MergeDuplicateGalleryTask(DownloadFragment fragment) {
        mFragment = new WeakReference<>(fragment);
        mContext = fragment.requireContext();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        DownloadFragment fragment = mFragment.get();
        if (fragment == null || fragment.isDetached()) {
            cancel(false);
            return;
        }

        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.settings_download_merge_duplicate_gallery);
        mProgressDialog.setMessage(getString(R.string.merge_scanning_galleries));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        // 初始化错误日志和合并日志
        initErrorLog();
        initMergeLog();
        
        try {
            // 步骤1：扫描已下载的画廊
            logInfo(getString(R.string.merge_start_scan));
            publishProgress(STEP_SCAN, getString(R.string.merge_scanning_galleries));
            if (!scanDownloadedGalleries()) {
                logError(getString(R.string.merge_scan_failed));
                return false;
 }
            logInfo(getString(R.string.merge_scan_complete, mGalleryGroups.size()));            // 步骤2：分析版本递进关系
            logInfo(getString(R.string.merge_start_analyze));
            publishProgress(STEP_ANALYZE, getString(R.string.merge_analyzing_versions));
            if (!analyzeGalleryVersions()) {
                logError(getString(R.string.merge_analyze_failed));
                return false;
 }
            logInfo(getString(R.string.merge_analyze_complete));            if (mGalleryGroups.isEmpty()) {
                logInfo("未发现需要合并的重复画廊");
                publishProgress("未发现需要合并的重复画廊");
                // 即使没有需要合并的画廊，也导出日志
                exportMergeLog();
                return true;
            }
            
            // 设置总数用于进度显示
            mTotalGalleries = mGalleryGroups.size();
            
            // 步骤3：备份数据库
            logInfo("开始备份数据库...");
            publishProgress(STEP_BACKUP, "正在备份数据库...");
            if (!backupDatabase()) {
                logError("备份数据库失败");
                return false;
            }
            logInfo("数据库备份成功");
            
            // 步骤4：合并画廊
            logInfo(getString(R.string.merge_start_merge));
            publishProgress(STEP_MERGE, getString(R.string.merge_merging_galleries));
            if (!mergeGalleries()) {
                logError(getString(R.string.merge_merge_failed));
                // 即使合并失败，也导出日志
                exportMergeLog();
                return false;
 }
            logInfo(getString(R.string.merge_merge_success));            // 导出合并日志
            exportMergeLog();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error merging duplicate galleries", e);
            logError(getString(R.string.merge_exception_occurred, e.getMessage()));
            logError(getString(R.string.merge_exception_stack, Log.getStackTraceString(e)));
            // 即使发生异常，也导出日志
            exportMergeLog();
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        DownloadFragment fragment = mFragment.get();
        if (fragment == null || fragment.isDetached()) {
            return;
        }

        if (values[0] instanceof Integer) {
            int step = (Integer) values[0];
            String message = (String) values[1];
            
            if (mProgressDialog != null) {
                mProgressDialog.setMessage(message);
                
                if (step == STEP_MERGE || step == STEP_BACKUP) {
                    // 合并阶段和备份阶段使用普通进度条
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setIndeterminate(false);
                    mProgressDialog.setMax(mTotalGalleries);
                    mProgressDialog.setProgress(mCurrentGallery);
                } else {
                    // 其他阶段使用无穷进度条
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mProgressDialog.setIndeterminate(true);
                }
            }
        } else if (values[0] instanceof String) {
            // 显示消息
            if (mProgressDialog != null) {
                mProgressDialog.setMessage((String) values[0]);
            }
        } else if (values[0] instanceof GalleryGroup) {
            // 显示确认对话框
            showConfirmDialog((GalleryGroup) values[0]);
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);
        DownloadFragment fragment = mFragment.get();
        if (fragment == null || fragment.isDetached()) {
            return;
        }

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (success) {
            if (mGalleryGroups.isEmpty()) {
                Toast.makeText(mContext, R.string.merge_no_duplicates_found, Toast.LENGTH_SHORT).show();
            } else {
                String message = getString(R.string.merge_success_summary, mMergedCount, mSkippedCount);
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
        } else {
            String message = getString(R.string.merge_failure_summary, mMergedCount, mSkippedCount);
            Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            // 生成错误报告文件
            generateErrorReport();
        }
    }

    /**
     * 显示确认对话框
     */
    private void showConfirmDialog(final GalleryGroup group) {
        DownloadFragment fragment = mFragment.get();
        if (fragment == null || fragment.isDetached()) {
            return;
        }

        Activity activity = fragment.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String title = group.galleries.get(0).title;
                String message = mContext.getString(R.string.merge_duplicate_gallery_confirm, title, group.galleries.size());
                
                new AlertDialog.Builder(activity)
                        .setTitle(getString(R.string.merge_duplicate_gallery_title))
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mUserResponse = RESPONSE_YES;
                                mUserResponseLatch.countDown();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mUserResponse = RESPONSE_NO;
                                mUserResponseLatch.countDown();
                            }
                        })
                        .setNeutralButton(R.string.merge_duplicate_gallery_merge_all, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mUserResponse = RESPONSE_YES_TO_ALL;
                                mUserResponseLatch.countDown();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                mUserResponse = RESPONSE_NO;
                                mUserResponseLatch.countDown();
                            }
                        })
                        .show();
            }
        });
    }

    /**
     * 扫描已下载的画廊
     */
    private boolean scanDownloadedGalleries() {
        UniFile downloadLocation = Settings.getDownloadLocation();
        if (downloadLocation == null) {
            logError("下载位置为空");
            return false;
        }
        
        logInfo("下载位置: " + downloadLocation.getUri());

        try {
            UniFile[] files = downloadLocation.listFiles();
            if (files == null) {
                logError("无法列出下载目录中的文件");
                return false;
            }
            
            logInfo("下载目录中共有 " + files.length + " 个文件/文件夹");

            Map<String, List<GalleryInfo>> titleMap = new HashMap<>();
            int validGalleryCount = 0;
            
            for (UniFile file : files) {
                if (!file.isDirectory()) {
                    continue;
                }

                String dirName = file.getName();
                if (dirName == null) {
                    continue;
                }

                // 解析文件夹名称，格式为 {gid}-{title}
                int dashIndex = dirName.indexOf('-');
                if (dashIndex <= 0 || dashIndex >= dirName.length() - 1) {
                    continue;
                }

                try {
                    long gid = Long.parseLong(dirName.substring(0, dashIndex));
                    String title = dirName.substring(dashIndex + 1);
                    
                    // 处理带🔄标识的标题
                    String originalTitle = title;
                    if (title.startsWith("🔄 ")) {
                        originalTitle = title.substring(3); // 移除"🔄 "前缀
                        logInfo("发现带🔄标识的文件夹，已移除标识: " + title + " -> " + originalTitle);
                    }
                    
                    // 检查是否有.ehviewer文件
                    UniFile ehviewerFile = file.findFile(".ehviewer");
                    if (ehviewerFile == null) {
                        logInfo("跳过文件夹 " + dirName + "（没有.ehviewer文件）");
                        continue;
                    }
                    
                    validGalleryCount++;
                    logInfo("发现有效画廊: " + originalTitle + " (GID: " + gid + ")");
                    
                    // 创建GalleryInfo对象
                    GalleryInfo galleryInfo = new GalleryInfo();
                    galleryInfo.gid = gid;
                    galleryInfo.title = originalTitle; // 使用处理后的标题
                    galleryInfo.token = ""; // 这里可以从.ehviewer文件读取
                    
                    // 按标题分组
                    String normalizedTitle = normalizeTitle(originalTitle);
                    if (!titleMap.containsKey(normalizedTitle)) {
                        titleMap.put(normalizedTitle, new ArrayList<>());
                    }
                    titleMap.get(normalizedTitle).add(galleryInfo);
                    
                } catch (NumberFormatException e) {
                    logInfo("跳过无法解析的文件夹名: " + dirName);
                }
            }
            
            logInfo("共找到 " + validGalleryCount + " 个有效画廊");
            
            // 找出有重复的画廊组
            for (List<GalleryInfo> galleries : titleMap.values()) {
                if (galleries.size() > 1) {
                    GalleryGroup group = new GalleryGroup();
                    group.galleries = galleries;
                    // 按GID排序，假设GID越大版本越新
                    Collections.sort(group.galleries, new Comparator<GalleryInfo>() {
                        @Override
                        public int compare(GalleryInfo g1, GalleryInfo g2) {
                            return Long.compare(g1.gid, g2.gid);
                        }
                    });
                    mGalleryGroups.add(group);
                    logInfo("发现重复画廊组: " + galleries.get(0).title + " (" + galleries.size() + " 个版本)");
                }
            }
            
            return true;
        } catch (Exception e) {
            logError("扫描过程中发生异常: " + e.getMessage());
            Log.e(TAG, "Error scanning downloaded galleries", e);
            return false;
        }
    }

    /**
     * 分析画廊版本关系
     */
    private boolean analyzeGalleryVersions() {
        // 这里可以添加更复杂的版本分析逻辑
        // 目前简单地认为同标题的画廊就是版本递进关系
        return true;
    }

    /**
     * 备份数据库
     */
    private boolean backupDatabase() {
        try {
            // 首先尝试使用 eh.db
            File dbFile = mContext.getDatabasePath("eh.db");
            if (!dbFile.exists()) {
                // 如果 eh.db 不存在，尝试 ehviewer.db
                dbFile = mContext.getDatabasePath("ehviewer.db");
                if (!dbFile.exists()) {
                    logError("数据库文件不存在，尝试导出数据库...");
                    // 如果数据库文件都不存在，尝试先导出数据库
                    return exportDatabaseForBackup();
                }
            }
            
            logInfo("数据库文件路径: " + dbFile.getAbsolutePath());
            logInfo("数据库文件大小: " + dbFile.length() + " bytes");

            File backupDir = new File(mContext.getCacheDir(), "db_backup");
            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    logError("无法创建备份目录: " + backupDir.getAbsolutePath());
                    return false;
                }
            }

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File backupFile = new File(backupDir, "ehviewer_backup_" + timestamp + ".db");
            logInfo("备份数据库到: " + backupFile.getAbsolutePath());

            // 复制数据库文件
            FileChannel source = null;
            FileChannel destination = null;
            long transferred = 0;
            try {
                source = new FileInputStream(dbFile).getChannel();
                destination = new FileOutputStream(backupFile).getChannel();
                transferred = destination.transferFrom(source, 0, source.size());
            } finally {
                // 确保通道被正确关闭
                if (source != null) {
                    try {
                        source.close();
                    } catch (IOException e) {
                        logError("关闭源文件通道失败: " + e.getMessage());
                    }
                }
                if (destination != null) {
                    try {
                        destination.close();
                    } catch (IOException e) {
                        logError("关闭目标文件通道失败: " + e.getMessage());
                    }
                }
            }
            
            if (transferred == dbFile.length()) {
                logInfo("数据库备份成功，备份大小: " + transferred + " bytes");
                return true;
            } else {
                logError("数据库备份不完整，原始大小: " + dbFile.length() + "，备份大小: " + transferred);
                return false;
            }
        } catch (IOException e) {
            logError("备份数据库时发生IO异常: " + e.getMessage());
            Log.e(TAG, "Error backing up database", e);
            return false;
        } catch (Exception e) {
            logError("备份数据库时发生异常: " + e.getMessage());
            Log.e(TAG, "Error backing up database", e);
            return false;
        }
    }
    
    /**
     * 导出数据库用于备份
     */
    private boolean exportDatabaseForBackup() {
        try {
            logInfo("开始导出数据库...");
            
            // 获取下载目录
            UniFile downloadLocation = Settings.getDownloadLocation();
            if (downloadLocation == null) {
                logError("无法获取下载位置");
                return false;
            }
            
            // 创建导出文件
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault());
            String fileName = "ehviewer_export_" + sdf.format(new Date()) + ".db";
            UniFile exportFile = downloadLocation.createFile(fileName);
            
            if (exportFile == null) {
                logError("无法创建导出文件");
                return false;
            }
            
            // 将 UniFile 转换为 File
            File tempFile = new File(mContext.getCacheDir(), "temp_export.db");
            
            // 使用 EhDB 的导出功能
            boolean exportSuccess = EhDB.exportDB(mContext, tempFile);
            
            if (exportSuccess) {
                // 复制到下载目录
                FileInputStream fis = new FileInputStream(tempFile);
                OutputStream os = exportFile.openOutputStream();
                IOUtils.copy(fis, os);
                fis.close();
                os.close();
                
                logInfo("数据库导出成功: " + exportFile.getUri());
                
                // 使用导出的文件作为备份
                return copyExportedFileToBackup(tempFile);
            } else {
                logError("EhDB.exportDB 失败");
                return false;
            }
        } catch (Exception e) {
            logError("导出数据库时发生异常: " + e.getMessage());
            Log.e(TAG, "Error exporting database", e);
            return false;
        }
    }
    
    /**
     * 导出数据库用于备份（兼容旧版本）
     */
    private boolean exportDatabaseForBackupLegacy() {
        try {
            logInfo("开始导出数据库（兼容旧版本）...");
            
            // 获取下载目录
            UniFile downloadLocation = Settings.getDownloadLocation();
            if (downloadLocation == null) {
                logError("无法获取下载位置");
                return false;
            }
            
            // 创建导出文件
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault());
            String fileName = "ehviewer_export_legacy_" + sdf.format(new Date()) + ".db";
            UniFile exportFile = downloadLocation.createFile(fileName);
            
            if (exportFile == null) {
                logError("无法创建导出文件");
                return false;
            }
            
            // 获取数据库路径
            File dbFile = mContext.getDatabasePath("eh.db");
            if (!dbFile.exists()) {
                logError("数据库文件不存在: " + dbFile.getAbsolutePath());
                return false;
            }
            
            // 直接复制数据库文件
            FileInputStream fis = new FileInputStream(dbFile);
            OutputStream os = exportFile.openOutputStream();
            IOUtils.copy(fis, os);
            fis.close();
            os.close();
            
            logInfo("数据库导出成功（兼容旧版本）: " + exportFile.getUri());
            return true;
        } catch (Exception e) {
            logError("导出数据库（兼容旧版本）时发生异常: " + e.getMessage());
            Log.e(TAG, "Error exporting database (legacy)", e);
            return false;
        }
    }
    
    /**
     * 将导出的文件复制到备份目录
     */
    private boolean copyExportedFileToBackup(File sourceFile) {
        try {
            File backupDir = new File(mContext.getCacheDir(), "db_backup");
            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    logError("无法创建备份目录: " + backupDir.getAbsolutePath());
                    return false;
                }
            }

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File backupFile = new File(backupDir, "ehviewer_backup_" + timestamp + ".db");
            logInfo("复制导出文件到备份: " + backupFile.getAbsolutePath());

            // 复制文件
            FileChannel source = new FileInputStream(sourceFile).getChannel();
            FileChannel destination = new FileOutputStream(backupFile).getChannel();
            long transferred = destination.transferFrom(source, 0, source.size());
            source.close();
            destination.close();
            
            // 删除临时文件
            sourceFile.delete();
            
            if (transferred == source.size()) {
                logInfo("导出文件备份成功，备份大小: " + transferred + " bytes");
                return true;
            } else {
                logError("导出文件备份不完整，原始大小: " + source.size() + "，备份大小: " + transferred);
                return false;
            }
        } catch (Exception e) {
            logError("复制导出文件到备份时发生异常: " + e.getMessage());
            Log.e(TAG, "Error copying exported file to backup", e);
            return false;
        }
    }

    /**
     * 合并画廊
     */
    private boolean mergeGalleries() {
        mTotalGalleries = mGalleryGroups.size();
        
        for (int i = 0; i < mGalleryGroups.size(); i++) {
            mCurrentGallery = i;
            GalleryGroup group = mGalleryGroups.get(i);
            
            // 询问用户是否合并
            if (!mMergeAll && !mSkipAll) {
                publishProgress(group);
                
                // 等待用户响应
                try {
                    if (!mUserResponseLatch.await(30, TimeUnit.SECONDS)) {
                        // 超时，默认不合并
                        mUserResponse = RESPONSE_NO;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                
                // 处理用户响应
                switch (mUserResponse) {
                    case RESPONSE_YES_TO_ALL:
                        mMergeAll = true;
                        logMergeInfo("用户选择全部合并");
                        break;
                    case RESPONSE_NO_TO_ALL:
                        mSkipAll = true;
                        logMergeInfo("用户选择全部跳过");
                        continue;
                    case RESPONSE_NO:
                        logMergeInfo("用户跳过画廊组: " + group.galleries.get(0).title);
                        mSkippedCount++;
                        continue;
                    case RESPONSE_YES:
                        logMergeInfo("用户合并画廊组: " + group.galleries.get(0).title);
                        break;
                }
                
                // 重置响应和锁存器
                mUserResponse = -1;
                mUserResponseLatch = new CountDownLatch(1);
            }
            
            if (mSkipAll) {
                continue;
            }
            
            // 执行合并逻辑
            if (!mergeGalleryGroup(group)) {
                logMergeError("合并画廊组失败: " + group.galleries.get(0).title);
                return false;
            }
            mMergedCount++;
        }
        
        return true;
    }

    /**
     * 合并单个画廊组
     */
    private boolean mergeGalleryGroup(GalleryGroup group) {
        try {
            // 找到最新的画廊（GID最大的）
            GalleryInfo latestGallery = group.galleries.get(group.galleries.size() - 1);
            logInfo("合并画廊组: " + latestGallery.title + " (最新GID: " + latestGallery.gid + ")");
            
            // 为其他画廊创建版本映射
            for (int i = 0; i < group.galleries.size() - 1; i++) {
                GalleryInfo oldGallery = group.galleries.get(i);
                logInfo("处理旧版本: " + oldGallery.title + " (GID: " + oldGallery.gid + ")");
                
                // 创建版本映射
                EhDB.addGalleryVersionMap(latestGallery.gid, oldGallery.gid, oldGallery.title);
                logInfo("已创建版本映射: " + oldGallery.gid + " -> " + latestGallery.gid);
                
                // 处理文件夹结构
                UniFile oldDir = SpiderDen.getGalleryDownloadDir(oldGallery);
                UniFile newDir = SpiderDen.getGalleryDownloadDir(latestGallery);
                
                if (oldDir != null && newDir != null) {
                    logInfo("处理文件夹: " + oldDir.getUri() + " -> " + newDir.getUri());
                    
                    // 创建.updateGallery文件
                    UniFile updateFile = oldDir.createFile(".updateGallery");
                    if (updateFile != null) {
                        String updateContent = "newGid=" + latestGallery.gid + "\n" +
                                "oldGid=" + oldGallery.gid + "\n" +
                                "title=" + latestGallery.title + "\n" +
                                "updateTime=" + System.currentTimeMillis() + "\n";
                        OutputStream os = updateFile.openOutputStream();
                        os.write(updateContent.getBytes("UTF-8"));
                        os.close();
                        logInfo("已创建.updateGallery文件");
                    } else {
                        logError("无法创建.updateGallery文件");
                    }
                    
                    // 复制.ehviewer文件
                    UniFile oldEhviewerFile = oldDir.findFile(".ehviewer");
                    if (oldEhviewerFile != null) {
                        InputStream is = oldEhviewerFile.openInputStream();
                        byte[] buffer = new byte[is.available()];
                        is.read(buffer);
                        is.close();
                        String oldEhviewerContent = new String(buffer, StandardCharsets.UTF_8);
                        String backupFileName = ".ehviewer." + oldGallery.gid;
                        UniFile backupFile = newDir.createFile(backupFileName);
                        if (backupFile != null) {
                            OutputStream os = backupFile.openOutputStream();
                            os.write(oldEhviewerContent.getBytes(StandardCharsets.UTF_8));
                            os.close();
                            logInfo("已备份.ehviewer文件: " + backupFileName);
                        } else {
                            logError("无法创建.ehviewer备份文件");
                        }
                    } else {
                        logError("找不到.ehviewer文件");
                    }
                } else {
                    if (oldDir == null) {
                        logError("无法获取旧画廊目录: GID " + oldGallery.gid);
                    }
                    if (newDir == null) {
                        logError("无法获取新画廊目录: GID " + latestGallery.gid);
                    }
                }
            }
            
            logInfo("画廊组合并完成: " + latestGallery.title);
            return true;
        } catch (Exception e) {
            logError("合并画廊组时发生异常: " + e.getMessage());
            Log.e(TAG, "Error merging gallery group", e);
            return false;
        }
    }

    /**
     * 初始化合并日志
     */
    private void initMergeLog() {
        mMergeLog = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        mMergeLog.append("=== 合并重复画廊操作日志 ===\n");
        mMergeLog.append("开始时间: ").append(timestamp).append("\n");
        mMergeLog.append("设备信息: Android ").append(android.os.Build.VERSION.RELEASE).append("\n");
        mMergeLog.append("设备型号: ").append(android.os.Build.MODEL).append("\n");
        mMergeLog.append("\n=== 操作记录 ===\n");
    }
    
    /**
     * 记录合并信息日志
     */
    private void logMergeInfo(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";
        mMergeLog.append(logEntry);
        Log.i(TAG, "[MERGE] " + message);
    }
    
    /**
     * 记录合并错误日志
     */
    private void logMergeError(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "] ERROR: " + message + "\n";
        mMergeLog.append(logEntry);
        Log.e(TAG, "[MERGE] " + message);
    }
    
    /**
     * 导出合并日志
     */
    private void exportMergeLog() {
        try {
            // 获取logcat目录
            File logcatDir = AppConfig.getExternalLogcatDir();
            if (logcatDir == null) {
                Log.e(TAG, "无法获取logcat目录");
                return;
            }
            
            // 创建合并日志文件
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "merge_gallery_log_" + sdf.format(new Date()) + ".log";
            File logFile = new File(logcatDir, fileName);
            
            // 添加统计信息
            mMergeLog.append("\n=== 统计信息 ===\n");
            mMergeLog.append("总画廊组数: ").append(mGalleryGroups.size()).append("\n");
            mMergeLog.append("已合并组数: ").append(mMergedCount).append("\n");
            mMergeLog.append("跳过组数: ").append(mSkippedCount).append("\n");
            mMergeLog.append("结束时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
            
            // 写入文件
            OutputStream os = new FileOutputStream(logFile);
            os.write(mMergeLog.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            Log.i(TAG, "合并日志已导出: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "导出合并日志失败", e);
        }
    }

    /**
     * 标准化标题，用于比较
     */
    private String normalizeTitle(String title) {
        // 处理带🔄标识的标题
        String normalizedTitle = title;
        if (title.startsWith("🔄 ")) {
            normalizedTitle = title.substring(3); // 移除"🔄 "前缀
        }
        
        // 移除特殊字符和空格，转换为小写
        return normalizedTitle.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "").toLowerCase();
    }
    
    /**
     * 初始化错误日志
     */
    private void initErrorLog() {
        mErrorLog = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        mErrorLog.append("=== 合并重复画廊错误报告 ===\n");
        mErrorLog.append("生成时间: ").append(timestamp).append("\n");
        mErrorLog.append("设备信息: Android ").append(android.os.Build.VERSION.RELEASE).append("\n");
        mErrorLog.append("设备型号: ").append(android.os.Build.MODEL).append("\n");
        mErrorLog.append("\n=== 执行日志 ===\n");
    }
    
    /**
     * 记录信息日志
     */
    private void logInfo(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "] INFO: " + message + "\n";
        mErrorLog.append(logEntry);
        Log.i(TAG, message);
    }
    
    /**
     * 记录错误日志
     */
    private void logError(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "] ERROR: " + message + "\n";
        mErrorLog.append(logEntry);
        mLastError = message;
        Log.e(TAG, message);
    }
    
    /**
     * 生成错误报告文件
     */
    private void generateErrorReport() {
        try {
            // 获取logcat目录（与导出logcat功能相同的位置）
            File logcatDir = AppConfig.getExternalLogcatDir();
            if (logcatDir == null) {
                Log.e(TAG, "无法获取logcat目录，尝试使用下载目录");
                // 如果无法获取logcat目录，回退到下载目录
                UniFile downloadLocation = Settings.getDownloadLocation();
                if (downloadLocation == null) {
                    Log.e(TAG, "无法获取下载目录");
                    return;
                }
                generateErrorReportInDownloadDir(downloadLocation);
                return;
            }
            
            // 创建错误报告文件
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "merge_gallery_error_" + sdf.format(new Date()) + ".log";
            File logFile = new File(logcatDir, fileName);
            
            if (logFile != null) {
                // 添加系统信息
                mErrorLog.append("\n=== 系统信息 ===\n");
                mErrorLog.append("日志目录: ").append(logcatDir.getAbsolutePath()).append("\n");
                
                // 添加环境信息
                Runtime runtime = Runtime.getRuntime();
                mErrorLog.append("\n=== JVM信息 ===\n");
                mErrorLog.append("最大内存: ").append(runtime.maxMemory() / 1024 / 1024).append(" MB\n");
                mErrorLog.append("已用内存: ").append((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).append(" MB\n");
                
                // 添加最后的错误
                mErrorLog.append("\n=== 最后错误 ===\n");
                mErrorLog.append(mLastError).append("\n");
                
                // 写入文件
                OutputStream os = new FileOutputStream(logFile);
                os.write(mErrorLog.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                
                Log.i(TAG, "错误报告已生成: " + logFile.getAbsolutePath());
            } else {
                Log.e(TAG, "无法创建错误报告文件");
            }
        } catch (Exception e) {
            Log.e(TAG, "生成错误报告失败", e);
        }
    }
    
    /**
     * 在下载目录生成错误报告（备用方案）
     */
    private void generateErrorReportInDownloadDir(UniFile downloadLocation) {
        try {
            // 创建错误报告文件
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "merge_gallery_error_" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadLocation.createFile(fileName);
            
            if (logFile != null) {
                // 添加系统信息
                mErrorLog.append("\n=== 系统信息 ===\n");
                mErrorLog.append("下载位置: ").append(downloadLocation.getUri()).append("\n");
                mErrorLog.append("可用空间: ").append(downloadLocation.length()).append(" bytes\n");
                
                // 添加环境信息
                Runtime runtime = Runtime.getRuntime();
                mErrorLog.append("\n=== JVM信息 ===\n");
                mErrorLog.append("最大内存: ").append(runtime.maxMemory() / 1024 / 1024).append(" MB\n");
                mErrorLog.append("已用内存: ").append((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).append(" MB\n");
                
                // 添加最后的错误
                mErrorLog.append("\n=== 最后错误 ===\n");
                mErrorLog.append(mLastError).append("\n");
                
                // 写入文件
                OutputStream os = logFile.openOutputStream();
                os.write(mErrorLog.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                
                Log.i(TAG, "错误报告已生成（下载目录）: " + logFile.getUri());
            } else {
                Log.e(TAG, "无法创建错误报告文件");
            }
        } catch (Exception e) {
            Log.e(TAG, "在下载目录生成错误报告失败", e);
        }
    }

    /**
     * 画廊组，包含具有版本递进关系的画廊
     */
    private static class GalleryGroup {
        List<GalleryInfo> galleries;
    }
}