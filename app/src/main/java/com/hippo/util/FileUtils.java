package com.hippo.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    public static boolean copyFile(File fromFile,File toFile){
        try{
            InputStream fileFrom = new FileInputStream(fromFile);
            OutputStream fileTo = new FileOutputStream(toFile);
            byte[] b = new byte[1024];
            int c;

            while ((c = fileFrom.read(b))>0){
                fileTo.write(b,0,c);
            }
            fileFrom.close();
            fileTo.close();

            return true;
        }catch (IOException ioException){
            ExceptionUtils.throwIfFatal(ioException);
            return false;
        }
    }
}
