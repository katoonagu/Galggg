package com.example.galggg.vpn;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class XrayRunner {

    public interface CrashListener {
        void onProcessCrashed(String name, int exitCode);
    }

    private static final String TAG = "XrayRunner";
    private static final String XRAY_NAME = "libxray.so";
    private static final String T2S_NAME = "libtun2socks.so";

    private final Context ctx;
    private final CrashListener crashListener;

    private Process xray;
    private Process t2s;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public XrayRunner(Context context, CrashListener listener) {
        this.ctx = context.getApplicationContext();
        this.crashListener = listener;
    }

    public void startAll(int tunFd, VlessLink v) throws Exception {
        stopAll();
        stopping.set(false);

        String libDir = ctx.getApplicationInfo().nativeLibraryDir;
        File xrayFile = new File(libDir, XRAY_NAME);
        File t2sFile = new File(libDir, T2S_NAME);
        Log.d(TAG, "nativeLibraryDir=" + libDir + " xray=" + xrayFile + " t2s=" + t2sFile);

        ensureBinaryExists(xrayFile);
        ensureBinaryExists(t2sFile);

        File cfg = writeXrayConfig(v);

        ProcessBuilder pbX = new ProcessBuilder(
                xrayFile.getAbsolutePath(),
                "run",
                "-c", cfg.getAbsolutePath(),
                "-format", "json"
        );
        pbX.redirectErrorStream(true);
        Process xp;
        try {
            xp = pbX.start();
        } catch (IOException io) {
            Log.e(TAG, "Unable to start xray: " + io.getMessage(), io);
            throw io;
        }
        this.xray = xp;
        pipeProcess("XrayProc", xp);
        watchProcess("xray", xp);

        try {
            String fd = String.valueOf(tunFd);
            ProcessBuilder pbT = new ProcessBuilder(
                    t2sFile.getAbsolutePath(),
                    "--tunFd", fd,
                    "--netif-ipaddr", "10.8.0.2",
                    "--netif-netmask", "255.255.255.0",
                    "--socksServer", "127.0.0.1:10808",
                    "--tunmtu", "1500",
                    "--loglevel", "info"
            );
            pbT.redirectErrorStream(true);
            Process tp;
            try {
                tp = pbT.start();
            } catch (Exception primary) {
                Log.w(TAG, "Primary tun2socks invocation failed (" + primary.getMessage() + "), trying fallback CLI");
                ProcessBuilder fallback = new ProcessBuilder(
                        t2sFile.getAbsolutePath(),
                        "--tundev", "fd://" + fd,
                        "--netif-ipaddr", "10.8.0.2",
                        "--netif-netmask", "255.255.255.0",
                        "--socks-server-addr", "127.0.0.1:10808",
                        "--socks5-udp"
                );
                fallback.redirectErrorStream(true);
                tp = fallback.start();
            }
            this.t2s = tp;
            pipeProcess("Tun2SocksProc", tp);
            watchProcess("tun2socks", tp);
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

    private void ensureBinaryExists(File file) throws IOException {
        if (!file.exists()) {
            String msg = "Native binary missing: " + file.getAbsolutePath();
            Log.e(TAG, msg);
            throw new IOException(msg);
        }
        if (!file.canExecute()) {
            String msg = "Native binary not executable: " + file.getAbsolutePath();
            Log.e(TAG, msg);
            throw new IOException(msg);
        }
        if (isPlaceholder(file)) {
            String msg = "Placeholder binary detected: " + file.getAbsolutePath();
            Log.e(TAG, msg);
            throw new IOException(msg);
        }
    }

    private File writeXrayConfig(VlessLink v) throws Exception {
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

        File cfgFile = new File(ctx.getCacheDir(), "xray_client.json");
        try (FileOutputStream fos = new FileOutputStream(cfgFile, false)) {
            byte[] json = root.toString(2).getBytes(StandardCharsets.UTF_8);
            fos.write(json);
            fos.getFD().sync();
        }
        Log.d(TAG, "xray client config at: " + cfgFile.getAbsolutePath());
        return cfgFile;
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

    private void pipeProcess(String tag, Process process) {
        if (process == null) return;
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Log.d(tag, line);
                }
            } catch (IOException e) {
                Log.e(tag, "pipe error", e);
            }
        }, tag + "-pipe");
        t.setDaemon(true);
        t.start();
    }

    private void watchProcess(String name, Process process) {
        if (process == null) return;
        Thread t = new Thread(() -> {
            try {
                int code = process.waitFor();
                if (!stopping.get() && code != 0 && crashListener != null) {
                    Log.e(TAG, name + " exited with code " + code);
                    crashListener.onProcessCrashed(name, code);
                } else {
                    Log.d(TAG, name + " exited with code " + code);
                }
            } catch (InterruptedException ignored) {
            }
        }, "watch-" + name);
        t.setDaemon(true);
        t.start();
    }

    public void stopAll() {
        stopping.set(true);
        destroyProcess(t2s, "tun2socks");
        t2s = null;
        destroyProcess(xray, "xray");
        xray = null;
    }

    private void destroyProcess(Process process, String name) {
        if (process == null) return;
        try {
            process.destroy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            } else {
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while stopping " + name, e);
        } catch (Exception e) {
            Log.w(TAG, "Error stopping " + name + ": " + e.getMessage(), e);
        }
    }
}
