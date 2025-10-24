package com.example.galggg.vpn;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import com.example.galggg.provision.ProvisionConstants;
import com.example.galggg.provision.ProvisionData;
import com.example.galggg.provision.XrayClientConfigBuilder;

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

    public void startAll(ParcelFileDescriptor tunPfd, ProvisionData data) throws Exception {
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

        File cfg = writeXrayConfig(data);

        ProcessBuilder pbX = new ProcessBuilder(
                xrayFile.getAbsolutePath(),
                "run",
                "-config", cfg.getAbsolutePath()
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
                        "-proxy", "socks5://127.0.0.1:" + ProvisionConstants.SOCKS_PORT,
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

    private File writeXrayConfig(ProvisionData data) throws Exception {
        String cfg = XrayClientConfigBuilder.build(data);
        if (cfg.length() > 0) {
            int previewLen = Math.min(cfg.length(), 256);
            Log.d(TAG, "xray config preview: " + cfg.substring(0, previewLen));
        }

        File dir = new File(ctx.getFilesDir(), ProvisionConstants.XRAY_DIR);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Failed to create Xray config dir: " + dir.getAbsolutePath());
        }

        File cfgFile = new File(dir, ProvisionConstants.XRAY_CFG);
        try (FileOutputStream fos = new FileOutputStream(cfgFile, false)) {
            byte[] json = cfg.getBytes(StandardCharsets.UTF_8);
            fos.write(json);
            fos.getFD().sync();
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

