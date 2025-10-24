package com.example.galggg.singbox;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class SBBinary {
    private SBBinary(){}

    public static File ensure(Context ctx) throws IOException {
        String abi = pickAbi();
        String assetPath = "bin/" + abi + "/sing-box";
        File out = new File(ctx.getFilesDir(), "bin/sing-box");

        if (!out.exists()) {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream is = ctx.getAssets().open(assetPath);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            }
        }
        // Ensure the binary is executable
        boolean ok = out.setExecutable(true, false);
        if (!ok) {
            Log.w("SBBinary", "setExecutable(false) returned false");
        }
        return out;
    }

    // Helper to unpack tun2socks from assets when an external binary is not provided
    public static File ensureTun2Socks(Context ctx) throws IOException {
        String abi = pickAbi();
        String assetPath = "bin/" + abi + "/tun2socks";
        File out = new File(ctx.getFilesDir(), "bin/tun2socks");
        if (!out.exists()) {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream is = ctx.getAssets().open(assetPath);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            }
        }
        out.setExecutable(true, false);
        return out;
    }

    private static String pickAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "x86_64".equals(abi)) {
                return abi;
            }
        }
        return "arm64-v8a";
    }
}
