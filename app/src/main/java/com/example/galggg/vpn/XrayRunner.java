package com.example.galggg.vpn;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class XrayRunner {
    private static final String TAG = "XrayRunner";
    private final Context ctx;
    private Process xray;
    private Process t2s;

    public XrayRunner(Context c) {
        this.ctx = c.getApplicationContext();
    }

    // === NEW: safe chmod for dir and files (owner-only 0700) with fallbacks ===
    private static void chmodExec(File dir, File... files) throws IOException {
        if (dir != null) {
            try {
                Os.chmod(dir.getAbsolutePath(), 0700);
            } catch (ErrnoException e) {
                try {
                    new ProcessBuilder("chmod", "700", dir.getAbsolutePath()).start().waitFor();
                } catch (Exception ignore) {
                }
            }
        }
        for (File f : files) {
            if (f == null) continue;
            f.setExecutable(true, true);
            f.setReadable(true, true);
            f.setWritable(true, true);
            try {
                Os.chmod(f.getAbsolutePath(), 0700);
            } catch (ErrnoException e) {
                try {
                    new ProcessBuilder("chmod", "700", f.getAbsolutePath()).start().waitFor();
                } catch (Exception ignore) {
                }
            }
            if (!f.canExecute()) {
                Log.e(TAG, "binary still not executable after chmod: " + f);
                throw new IOException("Not executable after chmod: " + f.getAbsolutePath());
            }
        }
    }

    // === NEW: copy asset to file (overwrites if content differs) ===
    private static void copyAsset(AssetManager am, String assetPath, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot mkdirs: " + parent);
        }
        try (InputStream in = am.open(assetPath);
             FileOutputStream fos = new FileOutputStream(out, false)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
            fos.getFD().sync();
        }
    }

    // === NEW: stage binaries from assets into app-private bin dir and chmod ===
    private static class StagePaths {
        final File binDir;
        final File xray;
        final File t2s;

        StagePaths(File binDir, File xray, File t2s) {
            this.binDir = binDir;
            this.xray = xray;
            this.t2s = t2s;
        }
    }

    private static StagePaths stageBinariesIfNeeded(Context ctx) throws IOException {
        File binDir = new File(ctx.getFilesDir(), "bin");
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new IOException("Cannot create bin dir: " + binDir);
        }

        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0]
                : Build.CPU_ABI;
        String abiDir;
        if (abi != null && abi.contains("64")) {
            abiDir = abi.startsWith("arm") ? "arm64-v8a" : "x86_64";
        } else {
            abiDir = (abi != null && abi.startsWith("arm")) ? "armeabi-v7a" : "x86";
        }
        if (!"arm64-v8a".equals(abiDir) && !"x86_64".equals(abiDir)) {
            abiDir = (abi != null && abi.contains("arm")) ? "arm64-v8a" : "x86_64";
        }
        String base = "bin/" + abiDir + "/";

        File xrayFile = new File(binDir, "xray");
        File t2sFile = new File(binDir, "tun2socks");

        AssetManager am = ctx.getAssets();

        if (!xrayFile.exists() || xrayFile.length() < 1024) {
            Log.d(TAG, "staging xray from assets: " + base + "xray -> " + xrayFile);
            copyAsset(am, base + "xray", xrayFile);
        }
        if (!t2sFile.exists() || t2sFile.length() < 1024) {
            Log.d(TAG, "staging tun2socks from assets: " + base + "tun2socks -> " + t2sFile);
            copyAsset(am, base + "tun2socks", t2sFile);
        }

        chmodExec(binDir, xrayFile, t2sFile);

        if (isPlaceholder(xrayFile) || isPlaceholder(t2sFile)) {
            throw new IOException("Placeholder binaries detected in " + binDir.getAbsolutePath());
        }

        Log.d(TAG, "binaries staged: xray=" + xrayFile.length() + " bytes, t2s=" + t2sFile.length() + " bytes");
        return new StagePaths(binDir, xrayFile, t2sFile);
    }

    public File ensureBin(String name) throws Exception {
        StagePaths sp = stageBinariesIfNeeded(ctx);
        File target;
        if ("xray".equals(name)) {
            target = sp.xray;
        } else if ("tun2socks".equals(name)) {
            target = sp.t2s;
        } else {
            target = new File(sp.binDir, name);
        }

        if (isPlaceholder(target)) {
            throw new IllegalStateException("Binary " + name + " is a placeholder; copy the real file into assets/bin");
        }

        return target;
    }

    private static boolean isPlaceholder(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[32];
            int n = fis.read(buf);
            if (n <= 0) return true;
            String first = new String(buf, 0, n).trim();
            return first.startsWith("PUT BINARY HERE");
        } catch (Exception e) {
            return false;
        }
    }

    public File writeXrayConfig(VlessLink v, File geoDir) throws Exception {
        JSONObject root = new JSONObject();

        JSONObject log = new JSONObject();
        log.put("loglevel", "warning");
        root.put("log", log);

        JSONObject inbound = new JSONObject();
        inbound.put("tag", "socks-in");
        inbound.put("listen", "127.0.0.1");
        inbound.put("port", 10808);
        inbound.put("protocol", "socks");
        JSONObject inboundSettings = new JSONObject();
        inboundSettings.put("udp", true);
        inbound.put("settings", inboundSettings);
        JSONArray inbounds = new JSONArray();
        inbounds.put(inbound);
        root.put("inbounds", inbounds);

        JSONObject user = new JSONObject();
        user.put("id", v.uuid);
        user.put("encryption", "none");
        if (v.flow != null) user.put("flow", v.flow);

        JSONArray users = new JSONArray();
        users.put(user);

        JSONObject vnextItem = new JSONObject();
        vnextItem.put("address", v.host);
        vnextItem.put("port", v.port);
        vnextItem.put("users", users);

        JSONArray vnextArr = new JSONArray();
        vnextArr.put(vnextItem);

        JSONObject outSettings = new JSONObject();
        outSettings.put("vnext", vnextArr);

        JSONObject stream = new JSONObject();
        stream.put("network", "tcp");
        stream.put("security", "reality");
        JSONObject reality = new JSONObject();
        reality.put("serverName", v.sni != null ? v.sni : "www.cloudflare.com");
        reality.put("publicKey", v.pbk);
        if (v.sid != null) reality.put("shortId", v.sid);
        reality.put("fingerprint", v.fp != null ? v.fp : "chrome");
        stream.put("realitySettings", reality);
        if (v.type != null) stream.put("type", v.type);

        JSONObject outbound = new JSONObject();
        outbound.put("tag", "vless-out");
        outbound.put("protocol", "vless");
        outbound.put("settings", outSettings);
        outbound.put("streamSettings", stream);

        JSONArray outbounds = new JSONArray();
        outbounds.put(outbound);

        JSONObject freedom = new JSONObject();
        freedom.put("protocol", "freedom");
        freedom.put("tag", "direct");
        outbounds.put(freedom);

        JSONObject block = new JSONObject();
        block.put("protocol", "blackhole");
        block.put("tag", "block");
        outbounds.put(block);

        root.put("outbounds", outbounds);

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put("1.1.1.1");
        servers.put("8.8.8.8");
        dns.put("servers", servers);
        root.put("dns", dns);

        File cfg = new File(ctx.getFilesDir(), "xray-client.json");
        try (FileWriter fw = new FileWriter(cfg, false)) {
            fw.write(root.toString(2));
        }
        return cfg;
    }

    public void startAll(int tunFd, VlessLink v) throws Exception {
        final Context ctxLocal = this.ctx;
        StagePaths sp = stageBinariesIfNeeded(ctxLocal);

        if (isPlaceholder(sp.xray) || isPlaceholder(sp.t2s)) {
            throw new IllegalStateException("Binaries are placeholders; replace asset files with real executables.");
        }

        final String XRAY_PATH = sp.xray.getAbsolutePath();
        final String T2S_PATH = sp.t2s.getAbsolutePath();

        File cfg = writeXrayConfig(v, ctxLocal.getFilesDir());
        ProcessBuilder xb = new ProcessBuilder(XRAY_PATH, "-c", cfg.getAbsolutePath());
        xb.redirectErrorStream(true);
        Process xp = xb.start();
        this.xray = xp;
        pipeToLogcat("xray", xp.getInputStream());

        try {
            String fd = String.valueOf(tunFd);
            ProcessBuilder tb = new ProcessBuilder(
                    T2S_PATH,
                    "--tunFd", fd,
                    "--socksServer", "127.0.0.1:10808",
                    "--loglevel", "info"
            );
            try {
                tb.redirectErrorStream(true);
                Process tp = tb.start();
                this.t2s = tp;
                pipeToLogcat("tun2socks", tp.getInputStream());
            } catch (Exception primary) {
                ProcessBuilder fallback = new ProcessBuilder(
                        T2S_PATH,
                        "--tundev", "fd://" + fd,
                        "--netif-ipaddr", "10.8.0.2",
                        "--netif-netmask", "255.255.255.0",
                        "--socks-server-addr", "127.0.0.1:10808",
                        "--socks5-udp"
                );
                fallback.redirectErrorStream(true);
                Process tp = fallback.start();
                this.t2s = tp;
                pipeToLogcat("tun2socks", tp.getInputStream());
            }
        } catch (Exception e) {
            if (xray != null) {
                try {
                    xray.destroy();
                } catch (Exception ignored) {
                }
                xray = null;
            }
            throw e;
        }
    }

    private void pipeToLogcat(final String tag, final InputStream in) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Log.i("XR-" + tag, line);
                }
            } catch (Exception ignored) {
            }
        }, "pipe-" + tag).start();
    }

    public void stopAll() {
        if (t2s != null) {
            try {
                t2s.destroy();
            } catch (Exception ignored) {
            }
            t2s = null;
        }
        if (xray != null) {
            try {
                xray.destroy();
            } catch (Exception ignored) {
            }
            xray = null;
        }
    }
}
