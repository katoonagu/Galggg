package com.example.galggg.vpn;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import com.example.galggg.BuildConfig;

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
    private ParcelFileDescriptor heldTunPfd; // keep the TUN fd open until stopAll()
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
        this.heldTunPfd = tunPfd;

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

        this.t2s = launchTun2SocksViaStdin(t2sFile.getAbsolutePath(), 1500, tunPfd);
        watchProcess("tun2socks", this.t2s);
    }

    /** Launch tun2socks by passing the TUN fd as STDIN (fd 0) to survive CLOEXEC_DEFAULT.
     *  We clear FD_CLOEXEC on the tun fd, dup2(tun, 0), exec, then restore original stdin.
     */
    private Process launchTun2SocksViaStdin(String t2sPath, int mtu, ParcelFileDescriptor tunPfd) throws IOException {
        final FileDescriptor tunFd = tunPfd.getFileDescriptor();
        try {
            // Clear FD_CLOEXEC on TUN, log before/after
            int before = Os.fcntlInt(tunFd, OsConstants.F_GETFD, 0);
            int after  = (before & ~OsConstants.FD_CLOEXEC);
            if (after != before) {
                Os.fcntlInt(tunFd, OsConstants.F_SETFD, after);
            }
            android.util.Log.d(TAG, "tunFd flags before=" + before + " after=" + after);

            FileDescriptor oldIn = Os.dup(FileDescriptor.in);
            try {
                Os.dup2(tunFd, OsConstants.STDIN_FILENO);

                List<String> args = Arrays.asList(
                        t2sPath,
                        "-device", "fd://0",
                        "-mtu", String.valueOf(mtu),
                        "-proxy", "socks5://127.0.0.1:10808",
                        "-loglevel", "info",
                        "-tcp-auto-tuning"
                );
                android.util.Log.d(TAG, "Starting tun2socks: " + args);

                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectErrorStream(false); // keep stdout/stderr for existing gobblers
                return pb.start();
            } finally {
                try { Os.dup2(oldIn, OsConstants.STDIN_FILENO); } catch (ErrnoException ignored) {}
                try { Os.close(oldIn); } catch (ErrnoException ignored) {}
            }
        } catch (ErrnoException e) {
            throw new IOException("Failed to launch tun2socks via stdin", e);
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

        // ----- logging block (DEBUG -> verbose with access to stdout) -----
        JSONObject log = new JSONObject();
        log.put("loglevel", BuildConfig.DEBUG ? "debug" : "warning");
        if (BuildConfig.DEBUG) {
            // push access/error streams to Logcat via stdout/stderr
            log.put("access", "/dev/stdout");
            log.put("error", "/dev/stderr");
        }
        root.put("log", log);
        // -----------------------------------------------------------------

        JSONObject dns = new JSONObject()
                .put("servers", new JSONArray()
                        .put("https+local://dns.google/dns-query")
                        .put("https+local://cloudflare-dns.com/dns-query"))
                .put("queryStrategy", "UseIPv4")
                .put("detour", "vless-out");
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
        JSONObject sniffing = new JSONObject();
        sniffing.put("enabled", true);
        sniffing.put("destOverride", new JSONArray().put("http").put("tls").put("quic"));
        inbound.put("sniffing", sniffing);
        JSONArray inbounds = new JSONArray();
        inbounds.put(inbound);
        root.put("inbounds", inbounds);

        JSONObject user = new JSONObject();
        user.put("id", v.uuid);
        user.put("encryption", "none");
        if (v.flow != null) {
            user.put("flow", v.flow);
        }

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
        if (v.sid != null) {
            reality.put("shortId", v.sid);
        }
        reality.put("fingerprint", v.fp != null ? v.fp : "chrome");
        stream.put("realitySettings", reality);
        if (v.type != null) {
            stream.put("type", v.type);
        }

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

        JSONArray rules = new JSONArray();

        // 1) DNS: UDP/53 из socks-in -> dns-out
        rules.put(new JSONObject()
                .put("type", "field")
                .put("inboundTag", new JSONArray().put("socks-in"))
                .put("network", "udp")
                .put("port", "53")
                .put("outboundTag", "dns-out"));

        // 2) Любой прочий UDP из socks-in -> block (глушим QUIC/UDP-443 и т.п.)
        rules.put(new JSONObject()
                .put("type", "field")
                .put("inboundTag", new JSONArray().put("socks-in"))
                .put("network", "udp")
                .put("outboundTag", "block"));

        // 3) Остальной трафик из socks-in (ТОЛЬКО TCP) -> vless-out
        rules.put(new JSONObject()
                .put("type", "field")
                .put("inboundTag", new JSONArray().put("socks-in"))
                .put("network", "tcp")
                .put("outboundTag", "vless-out"));

        JSONObject routing = new JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules);

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
        try {
            Os.chmod(cfgFile.getAbsolutePath(), 0644);
        } catch (ErrnoException e) {
            Log.w(TAG, "chmod config failed: " + e.getMessage(), e);
        }
        Log.d(TAG, "dns-mode=DoH via dns-out; servers=[dns.google, cloudflare-dns.com]");
        Log.d(TAG, "routing: UDP except 53 is blocked to force TCP fallback (QUIC disabled)");
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
        if (heldTunPfd != null) {
            try {
                heldTunPfd.close();
            } catch (Exception ignore) {
            }
            heldTunPfd = null;
        }
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

