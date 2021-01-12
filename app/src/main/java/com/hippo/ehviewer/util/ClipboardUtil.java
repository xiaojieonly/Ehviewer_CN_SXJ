package com.hippo.ehviewer.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.util.ExceptionUtils;

public class ClipboardUtil {
    /**
     * 实现文本复制功能
     *
     * @param galleryInfo 复制的对象
     */
    public static void copy(GalleryInfo galleryInfo) {
        String content = reduceString(galleryInfo);
        if (!TextUtils.isEmpty(content)) {
            // 得到剪贴板管理器
            ClipboardManager cmb = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
            cmb.setText(content.trim());
            // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）
            ClipData clipData = ClipData.newPlainText(null, content);
            // 把数据集设置（复制）到剪贴板
            cmb.setPrimaryClip(clipData);
        }
    }

    /**
     * 清空剪贴板内容
     */
    public static void clearClipboard() {
        ClipboardManager manager = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            try {
                manager.setPrimaryClip(manager.getPrimaryClip());
                manager.setText(null);
            } catch (Exception e) {
                ExceptionUtils.getReadableString(e);
            }
        }
    }

    public static GalleryInfo getGalleryInfoFromClip(){
        GalleryInfo galleryInfo = new GalleryInfo();

        String galleryString = getClipContent();
        clearClipboard();

        return galleryInfo;
    }

    private static String reduceString(GalleryInfo galleryInfo){
        String s = "hello world!";

        return s;
    }

    /**
     * 获取系统剪贴板内容
     */
    private static String getClipContent() {
        ClipboardManager manager = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            if (manager.hasPrimaryClip() && manager.getPrimaryClip().getItemCount() > 0) {
                CharSequence addedText = manager.getPrimaryClip().getItemAt(0).getText();
                String addedTextString = String.valueOf(addedText);
                if (!TextUtils.isEmpty(addedTextString)) {
                    return addedTextString;
                }
            }
        }
        return "";
    }
}
