package com.hippo.unifile;

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.io.Closeable;

class Utils {

    static void closeQuietly(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable e) {
                throwIfFatal(e);
            }
        }
    }

    static void closeQuietly(Closeable is) {
        if (is != null) {
            try {
                is.close();
            } catch (Throwable e) {
                throwIfFatal(e);
            }
        }
    }

    static void throwIfFatal(@NonNull Throwable t) {
        // values here derived from https://github.com/ReactiveX/RxJava/issues/748#issuecomment-32471495
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        } else if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        } else if (t instanceof LinkageError) {
            throw (LinkageError) t;
        }
    }
}
