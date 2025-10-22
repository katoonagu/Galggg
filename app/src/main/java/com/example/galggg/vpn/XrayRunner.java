package com.example.galggg.vpn;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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

    public void startAll(ParcelFileDescriptor tunPfd, VlessLink v) throws Exception {
        stopAll();
        stopping.set(false);

        String libDir = ctx.getApplicationInfo().nativeLibraryDir;
        File xrayFile = new File(libDir, XRAY_NAME);
        File t2sFile = new File(libDir, T2S_NAME);
        Log.d(TAG, "nativeLibraryDir=" + libDir + " xray=" + xrayFile + " t2s=" + t2sFile);

        ensureBinaryExists(xrayFile);
        ensureBinaryExists(t2sFile);

        if (tunPfd == null) {
            throw new IllegalArgumentException("ParcelFileDescriptor for TUN must not be null");
        }
        final int tunFd = tunPfd.getFd();
        final java.io.FileDescriptor tunFdObj = tunPfd.getFileDescriptor();
        int beforeFlags = -1;
        int afterFlags = -1;
        try {
            beforeFlags = Os.fcntlInt(tunFdObj, OsConstants.F_GETFD, 0);
            Os.fcntlInt(tunFdObj, OsConstants.F_SETFD, 0);
            afterFlags = Os.fcntlInt(tunFdObj, OsConstants.F_GETFD, 0);
        } catch (ErrnoException e) {
            Log.w(TAG, "Unable to clear FD_CLOEXEC on tunFd: " + e.getMessage(), e);
        }
        Log.d(TAG, "tunFd flags before=" + beforeFlags + " after=" + afterFlags);

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

        final int socksPort = 10808;
        final String socksAddr = "socks5://127.0.0.1:" + socksPort;
        final String t2sPath = t2sFile.getAbsolutePath();
        final List<String> t2sArgs = Arrays.asList(
                t2sPath,
                "-device", "fd://0",
                "-mtu", "1500",
                "-proxy", socksAddr,
                "-loglevel", "info",
                "-tcp-auto-tuning"
        );
        Log.d(TAG, "Starting tun2socks: " + t2sArgs);
        ProcessBuilder pbT = new ProcessBuilder(t2sArgs);
        pbT.redirectErrorStream(true);
        pbT.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process tp = null;
        FileDescriptor savedIn = null;
        try {
            savedIn = Os.dup(FileDescriptor.in);
        } catch (ErrnoException e) {
            Log.w(TAG, "Unable to duplicate stdin: " + e.getMessage(), e);
        }
        try {
            Os.dup2(tunPfd.getFileDescriptor(), 0);
            try {
                tp = pbT.start();
            } catch (IOException io) {
                Log.e(TAG, "Unable to start tun2socks: " + io.getMessage(), io);
                throw io;
            }
        } catch (ErrnoException e) {
            Log.e(TAG, "dup2 to stdin failed: " + e.getMessage(), e);
            throw new IOException("dup2 failed", e);
        } finally {
            if (savedIn != null) {
                try {
                    Os.dup2(savedIn, 0);
                } catch (ErrnoException e) {
                    Log.w(TAG, "Unable to restore stdin: " + e.getMessage(), e);
                }
                try {
                    Os.close(savedIn);
                } catch (ErrnoException ignored) {
                }
            }
        }
        this.t2s = tp;
        pipeProcess("Tun2SocksProc", tp);
        watchProcess("tun2socks", tp);
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

        JSONObject dns = new JSONObject();
        JSONArray dnsServers = new JSONArray();
        JSONObject dohGoogle = new JSONObject();
        dohGoogle.put("address", "https://dns.google/dns-query");
        dohGoogle.put("skipFallback", true);
        dohGoogle.put("detour", "vless-out");
        dnsServers.put(dohGoogle);
        JSONObject dohCloudflare = new JSONObject();
        dohCloudflare.put("address", "https://cloudflare-dns.com/dns-query");
        dohCloudflare.put("skipFallback", true);
        dohCloudflare.put("detour", "vless-out");
        dnsServers.put(dohCloudflare);
        JSONObject dohLocal = new JSONObject();
        dohLocal.put("address", "localhost");
        dnsServers.put(dohLocal);
        dns.put("servers", dnsServers);
        dns.put("queryStrategy", "UseIP");
        dns.put("disableCache", false);
        dns.put("tag", "builtin-dns");
        root.put("dns", dns);

        JSONObject inbound = new JSONObject();
        inbound.put("tag", "socks-in");
        inbound.put("listen", "127.0.0.1");
        inbound.put("port", 10808);
        inbound.put("protocol", "socks");
        JSONObject inboundSettings = new JSONObject();
        inboundSettings.put("udp", true);
        inboundSettings.put("auth", "noauth");
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

        JSONObject dnsOut = new JSONObject();
        dnsOut.put("protocol", "dns");
        dnsOut.put("tag", "dns-out");
        outbounds.put(dnsOut);

        JSONObject freedom = new JSONObject();
        freedom.put("protocol", "freedom");
        freedom.put("tag", "direct");
        outbounds.put(freedom);

        JSONObject block = new JSONObject();
        block.put("protocol", "blackhole");
        block.put("tag", "block");
        outbounds.put(block);

        root.put("outbounds", outbounds);

        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "IPIfNonMatch");
        JSONArray rules = new JSONArray();

        JSONObject dnsRule = new JSONObject();
        dnsRule.put("type", "field");
        dnsRule.put("inboundTag", new JSONArray().put("socks-in"));
        dnsRule.put("port", "53");
        dnsRule.put("network", "udp");
        dnsRule.put("outboundTag", "dns-out");
        rules.put(dnsRule);

        JSONObject defaultRule = new JSONObject();
        defaultRule.put("type", "field");
        defaultRule.put("inboundTag", new JSONArray().put("socks-in"));
        defaultRule.put("outboundTag", "vless-out");
        rules.put(defaultRule);

        routing.put("rules", rules);
        root.put("routing", routing);

        File cfgFile = new File(ctx.getCacheDir(), "xray_client.json");
        String jsonPretty = root.toString(2);
        try (FileOutputStream fos = new FileOutputStream(cfgFile, false)) {
            byte[] json = jsonPretty.getBytes(StandardCharsets.UTF_8);
            fos.write(json);
            fos.getFD().sync();
        }
        cfgFile.setReadable(true, false);
        Log.d(TAG, "xray client config at: " + cfgFile.getAbsolutePath());
        Log.d(TAG, "dns-mode=DoH via dns-out; servers=[dns.google, cloudflare-dns.com]");
        if (jsonPretty.length() > 0) {
            int previewLen = Math.min(jsonPretty.length(), 2000);
            Log.d(TAG, "xray config preview: " + jsonPretty.substring(0, previewLen));
        }
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
