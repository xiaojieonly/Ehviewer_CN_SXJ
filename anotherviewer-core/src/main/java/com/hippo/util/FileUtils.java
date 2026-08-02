package com.hippo.util;

import java.io.*;

public class FileUtils {
    public static File getFileDir(String dir) {
        File f = new File(dir);
        if (!f.exists()) f.mkdirs();
        return f;
    }
    public static boolean delete(File file) { return file != null && file.delete(); }
    public static boolean copyFile(File src, File dest) {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            return true;
        } catch (IOException e) { return false; }
    }
}
