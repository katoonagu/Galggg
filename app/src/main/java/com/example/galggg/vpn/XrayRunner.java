package com.example.galggg.vpn;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

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

        this.t2s = startTun2Socks(t2sFile, tunPfd);
        watchProcess("tun2socks", this.t2s);
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

        File cfgFile = new File(ctx.getCacheDir(), "xray_client.json");
        String jsonPretty = patchConfigForDoH(root.toString());
        Files.write(cfgFile.toPath(), jsonPretty.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING);
        Os.chmod(cfgFile.getAbsolutePath(), 0644);
        Log.d(TAG, "xray client config at: " + cfgFile.getAbsolutePath());
        Log.d(TAG, "dns-mode=DoH via dns-out; servers=[dns.google, cloudflare-dns.com]");
        if (jsonPretty.length() > 0) {
            int previewLen = Math.min(jsonPretty.length(), 2000);
            Log.d(TAG, "xray config preview: " + jsonPretty.substring(0, previewLen));
        }
        return cfgFile;
    }

    private static String patchConfigForDoH(String cfgJson) throws JSONException {
        JSONObject root = new JSONObject(cfgJson);

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put("https+local://dns.google/dns-query");
        servers.put("https+local://cloudflare-dns.com/dns-query");
        dns.put("servers", servers);
        dns.put("queryStrategy", "UseIPv4");
        dns.put("detour", "vless-out");
        root.put("dns", dns);

        JSONArray inbounds = root.optJSONArray("inbounds");
        if (inbounds == null) inbounds = new JSONArray();
        boolean hasSocks = false;
        for (int i = 0; i < inbounds.length(); i++) {
            JSONObject in = inbounds.getJSONObject(i);
            if ("socks".equals(in.optString("protocol"))) {
                hasSocks = true;
                in.put("tag", "socks-in");
                JSONObject settings = in.optJSONObject("settings");
                if (settings == null) settings = new JSONObject();
                settings.put("udp", true);
                in.put("settings", settings);

                JSONObject sniffing = new JSONObject();
                sniffing.put("enabled", true);
                JSONArray dest = new JSONArray();
                dest.put("http");
                dest.put("tls");
                dest.put("quic");
                sniffing.put("destOverride", dest);
                in.put("sniffing", sniffing);

                if (!in.has("listen")) in.put("listen", "127.0.0.1");
                if (!in.has("port")) in.put("port", 10808);
            }
        }
        if (!hasSocks) {
            JSONObject in = new JSONObject();
            in.put("tag", "socks-in");
            in.put("protocol", "socks");
            in.put("listen", "127.0.0.1");
            in.put("port", 10808);
            JSONObject settings = new JSONObject().put("udp", true);
            in.put("settings", settings);
            JSONObject sniffing = new JSONObject()
                    .put("enabled", true)
                    .put("destOverride", new JSONArray().put("http").put("tls").put("quic"));
            in.put("sniffing", sniffing);
            inbounds.put(in);
        }
        root.put("inbounds", inbounds);

        JSONArray outbounds = root.optJSONArray("outbounds");
        if (outbounds == null) outbounds = new JSONArray();
        java.util.Set<String> tags = new java.util.HashSet<>();
        for (int i = 0; i < outbounds.length(); i++) {
            tags.add(outbounds.getJSONObject(i).optString("tag"));
        }
        if (!tags.contains("dns-out")) outbounds.put(new JSONObject().put("tag", "dns-out").put("protocol", "dns"));
        if (!tags.contains("direct")) outbounds.put(new JSONObject().put("tag", "direct").put("protocol", "freedom"));
        root.put("outbounds", outbounds);

        JSONObject routing = root.optJSONObject("routing");
        if (routing == null) routing = new JSONObject();
        JSONArray rules = routing.optJSONArray("rules");
        if (rules == null) rules = new JSONArray();

        JSONArray newRules = new JSONArray();
        newRules.put(new JSONObject()
                .put("type", "field")
                .put("inboundTag", new JSONArray().put("socks-in"))
                .put("network", "udp")
                .put("port", "53")
                .put("outboundTag", "dns-out"));

        JSONArray dohDomains = new JSONArray();
        dohDomains.put("dns.google");
        dohDomains.put("cloudflare-dns.com");
        dohDomains.put("chrome.cloudflare-dns.com");
        dohDomains.put("www.cloudflare-dns.com");
        newRules.put(new JSONObject()
                .put("type", "field")
                .put("domain", dohDomains)
                .put("outboundTag", "vless-out"));

        newRules.put(new JSONObject()
                .put("type", "field")
                .put("inboundTag", new JSONArray().put("socks-in"))
                .put("outboundTag", "vless-out"));

        for (int i = 0; i < rules.length(); i++) {
            newRules.put(rules.get(i));
        }
        routing.put("domainStrategy", "AsIs");
        routing.put("rules", newRules);
        root.put("routing", routing);

        return root.toString();
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

    private Process startTun2Socks(File t2sFile, ParcelFileDescriptor pfd) throws Exception {
        final int tunFd = pfd.getFd();
        final FileDescriptor fdObj = pfd.getFileDescriptor();
        final int before = Os.fcntlInt(fdObj, OsConstants.F_GETFD, 0);
        if ((before & OsConstants.FD_CLOEXEC) != 0) {
            Os.fcntlInt(fdObj, OsConstants.F_SETFD, before & ~OsConstants.FD_CLOEXEC);
        }
        final int after = Os.fcntlInt(fdObj, OsConstants.F_GETFD, 0);
        Log.d(TAG, "tunFd flags before=" + before + " after=" + after);

        List<String> t2sArgs = Arrays.asList(
                t2sFile.getAbsolutePath(),
                "-device", "fd://" + tunFd,
                "-mtu", "1500",
                "-proxy", "socks5://127.0.0.1:10808",
                "-loglevel", "info",
                "-tcp-auto-tuning"
        );
        Log.d(TAG, "Starting tun2socks: " + t2sArgs);

        ProcessBuilder pb = new ProcessBuilder(t2sArgs);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        pipeProcess("Tun2SocksProc", proc);
        return proc;
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
