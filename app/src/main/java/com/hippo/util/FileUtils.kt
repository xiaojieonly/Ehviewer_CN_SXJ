package com.hippo.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import static com.hippo.ehviewer.client.EhConfig.TORRENT_PATH;

import com.hippo.unifile.UniFile;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class FileUtils {
    public static boolean copyFile(File fromFile, File toFile) {
        return copyFile(fromFile, toFile, false);
    }

    public static boolean copyFile(File fromFile, File toFile, boolean flush) {
        try {
            InputStream fileFrom = new FileInputStream(fromFile);
            OutputStream fileTo = new FileOutputStream(toFile);
            byte[] b = new byte[1024];
            int c;

            while ((c = fileFrom.read(b)) > 0) {
                fileTo.write(b, 0, c);
            }
            if (flush) {
                fileTo.flush();
            }
            fileFrom.close();
            fileTo.close();

            return true;
        } catch (IOException ioException) {
            ExceptionUtils.throwIfFatal(ioException);
            FirebaseCrashlytics.getInstance().recordException(ioException);
            return false;
        }
    }

    public static boolean copyFile(UniFile fromFile, UniFile toFile, boolean flush) {
        try {
            InputStream fileFrom = fromFile.openInputStream();
            OutputStream fileTo = toFile.openOutputStream();
            byte[] b = new byte[1024];
            int c;

            while ((c = fileFrom.read(b)) > 0) {
                fileTo.write(b, 0, c);
            }
            if (flush) {
                fileTo.flush();
            }
            fileFrom.close();
            fileTo.close();

            return true;
        } catch (IOException ioException) {
            ExceptionUtils.throwIfFatal(ioException);
            FirebaseCrashlytics.getInstance().recordException(ioException);
            return false;
        }
    }

    /**
     * 打开目录
     *
     * @param path
     * @param context
     */
    public static void openAssignFolder(String path, Context context) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        Intent intent;

//        Uri uri = Uri.parse(path);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload/"+TORRENT_PATH);
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
        }else{
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.setDataAndType(Uri.fromFile(file), "file/*");
        }


        try {
//            context.startActivity(intent);
            context.startActivity(Intent.createChooser(intent, "选择浏览工具"));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String getPath(Context context, Uri uri) {
        String path = "";
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        if (cursor.moveToFirst()) {
            try {
                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        cursor.close();
        return path;
    }

}
