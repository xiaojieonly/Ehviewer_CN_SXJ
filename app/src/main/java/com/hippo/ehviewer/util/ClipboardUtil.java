package com.hippo.ehviewer.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;
import com.hippo.util.ExceptionUtils;

import java.util.zip.DataFormatException;

public class ClipboardUtil {

    private static final JSONObject defaultInfo ;

    static {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("favoriteName",null);
        jsonObject.put("favoriteSlot",-2);
        jsonObject.put("pages",0);
        jsonObject.put("rated",false);
        jsonObject.put("simpleTags",null);
        jsonObject.put("thumbWidth",0);
        jsonObject.put("thumbHeight",0);
        jsonObject.put("spanSize",0);
        jsonObject.put("spanIndex",0);
        jsonObject.put("spanGroupIndex",0);

        defaultInfo = jsonObject;
    }


    /**
     * 实现文本复制功能
     *
     * @param galleryInfo 复制的对象
     */
    public static void copy(GalleryInfo galleryInfo) {
        //对象转换
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
     * 从剪切板获取数据
     * @return
     */
    public static GalleryInfo getGalleryInfoFromClip() {
        GalleryInfo galleryInfo = new GalleryInfo();

        String galleryString = null;
        try {
            galleryString = new String(GZIPUtils.uncompress(getClipContent().getBytes()));
        } catch (DataFormatException e) {
            e.printStackTrace();
        }
        clearClipboard();
        JSONObject object = (JSONObject) JSONObject.parse(galleryString);
        object.putAll(defaultInfo);
        return JSON.toJavaObject(object,GalleryInfo.class);
    }

    private static String reduceString(GalleryInfo galleryInfo){
//        String s = "hello world!";
        LocalFavoriteInfo localFavoriteInfo = (LocalFavoriteInfo)galleryInfo;
        String s = JSON.toJSONString(localFavoriteInfo);


        String zipString = new String(GZIPUtils.compress(s.getBytes()));
        return zipString;



    }

    /**
     * 清空剪贴板内容
     */
    private static void clearClipboard() {
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
