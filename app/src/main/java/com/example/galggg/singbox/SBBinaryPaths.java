package com.example.galggg.singbox;

import android.content.Context;

import java.io.File;

public final class SBBinaryPaths {
    private SBBinaryPaths(){}

    public static File singbox(Context ctx) {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libsingbox.so");
    }

    public static File tun2socks(Context ctx) {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libtun2socks.so");
    }
}
