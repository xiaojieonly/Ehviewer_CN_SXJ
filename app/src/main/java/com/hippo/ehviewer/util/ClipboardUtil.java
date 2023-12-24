package com.hippo.ehviewer.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;
import com.hippo.util.ExceptionUtils;


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

        clearClipboard();
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
     * 实现文本复制功能
     *
     * @param text 复制的文本
     */
    public static void copyText(String text) {

        clearClipboard();
        if (!TextUtils.isEmpty(text)) {
            // 得到剪贴板管理器
            ClipboardManager cmb = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）
            ClipData clipData = ClipData.newPlainText(null, text);
            // 把数据集设置（复制）到剪贴板
            cmb.setPrimaryClip(clipData);
        }
    }


    /**
     * 从剪切板获取数据
     * @return
     */
    public static GalleryInfo getGalleryInfoFromClip() {

        String compressString = getClipContent();

        String galleryString = GZIPUtils.uncompress(compressString);


        clearClipboard();
        JSONObject object = (JSONObject) JSONObject.parse(galleryString);

        if (object == null){
            return null;
        }
        object.putAll(defaultInfo);
        object.put("time",System.currentTimeMillis());
        return JSON.toJavaObject(object,GalleryInfo.class);
    }

    private static String reduceString(GalleryInfo galleryInfo){
//        String s = "hello world!";
        LocalFavoriteInfo localFavoriteInfo = (LocalFavoriteInfo)galleryInfo;
        JSONObject favoriteJson = (JSONObject) JSONObject.toJSON(localFavoriteInfo);

        favoriteJson.remove("favoriteName");
        favoriteJson.remove("pages");
        favoriteJson.remove("rated");
        favoriteJson.remove("spanGroupIndex");
        favoriteJson.remove("spanIndex");
        favoriteJson.remove("spanSize");
        favoriteJson.remove("thumbHeight");
        favoriteJson.remove("thumbWidth");
        favoriteJson.remove("time");
        favoriteJson.remove("favoriteSlot");



        String s = JSONObject.toJSONString(favoriteJson);
        String s1 = new String(Base64.encode(s.getBytes(),Base64.DEFAULT));

        return GZIPUtils.compress(s);
//        return c;
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
