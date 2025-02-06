package com.hippo

import java.io.FileDescriptor

object Native {
    @JvmStatic
    fun initialize() {
        System.loadLibrary("ehviewer")
    }

    @JvmStatic
    external fun getFd(fd: FileDescriptor?): Int
}