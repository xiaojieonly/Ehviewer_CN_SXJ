package com.hippo.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static androidx.core.app.ActivityCompat.startActivityForResult;

public class FileUtils {

    public static boolean copyFile(File fromFile, File toFile) {
        try {
            InputStream fileFrom = new FileInputStream(fromFile);
            OutputStream fileTo = new FileOutputStream(toFile);
            byte[] b = new byte[1024];
            int c;
            while ((c = fileFrom.read(b)) > 0) {
                fileTo.write(b, 0, c);
            }
            fileFrom.close();
            fileTo.close();
            return true;
        } catch (IOException ioException) {
            ExceptionUtils.throwIfFatal(ioException);
            return false;
        }
    }

    /**
     * 打开目录
     * @param path
     * @param context
     */
    public static void openAssignFolder(String path, Context context) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload");
        //        Uri uri = Uri.parse(path);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
        //
        //        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        //        intent.addCategory(Intent.CATEGORY_DEFAULT);
        //        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //        intent.setDataAndType(Uri.fromFile(file), "file/*");
        try {
            //            context.startActivity(intent);
            context.startActivity(Intent.createChooser(intent, "选择浏览工具"));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }
}
