package com.example.galggg.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.galggg.R;
import com.example.galggg.singbox.SingBoxRunner;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GalgggVpnService extends VpnService {

    public static final String ACTION_START = "com.example.galggg.vpn.START";
    public static final String ACTION_STOP = "com.example.galggg.vpn.STOP";
    private static final String CH_ID = "galggg_vpn";
    private static final int NOTIF_ID = 101;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ParcelFileDescriptor tunPfd;
    private FileDescriptor tunDupFd;
    private FileDescriptor savedStdinFd;

    public static boolean isActive() {
        return ACTIVE.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Foreground started on demand from onStartCommand.
    }

    private void prepareTunOrThrow() throws Exception {
        if (tunPfd != null) {
            try {
                tunPfd.close();
            } catch (Exception ignore) {
            }
            tunPfd = null;
        }
        if (tunDupFd != null) {
            try {
                Os.close(tunDupFd);
            } catch (ErrnoException ignore) {
            }
            tunDupFd = null;
        }

        VpnService.Builder b = new VpnService.Builder();
        b.setSession("Galggg");
        b.addAddress("10.0.0.2", 32);
        b.addDnsServer("1.1.1.1");
        b.addRoute("0.0.0.0", 0);
        b.addRoute("::", 0);
        try {
            b.addDisallowedApplication(getPackageName());
        } catch (Exception ignore) {
        }

        tunPfd = b.establish();
        if (tunPfd == null) {
            throw new IllegalStateException("establish() returned null");
        }

        FileDescriptor rawFd = tunPfd.getFileDescriptor();
        int flagsBefore = Os.fcntlInt(rawFd, OsConstants.F_GETFD, 0);
        tunDupFd = Os.dup(rawFd);
        Os.fcntlInt(tunDupFd, OsConstants.F_SETFD, 0);
        int flagsAfter = Os.fcntlInt(tunDupFd, OsConstants.F_GETFD, 0);

        Log.d("GalgggVpnService",
                "TUN fd raw=" + rawFd + " dup=" + tunDupFd
                        + " flagsBefore=" + flagsBefore + " flagsAfter=" + flagsAfter);
    }

    private void remapTunToStdin() throws Exception {
        if (tunDupFd == null) {
            throw new IllegalStateException("tun fd is not ready for remap");
        }
        savedStdinFd = Os.dup(FileDescriptor.in);
        FileDescriptor dup = Os.dup(tunDupFd);
        Os.fcntlInt(dup, OsConstants.F_SETFD, 0);
        Os.dup2(dup, OsConstants.STDIN_FILENO);
        Os.close(dup);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopTunAndRunner();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification());
        try {
            prepareTunOrThrow();
            remapTunToStdin();
            String tunUri = getTunDeviceArg();
            String t2s = getTun2SocksPath();
            SingBoxRunner.startAll(getApplicationContext(), tunUri, t2s);
            ACTIVE.set(true);
            Log.d("GalgggVpnService", "VPN engines started");
        } catch (Throwable t) {
            Log.e("GalgggVpnService", "start failed", t);
            cleanupTunResources();
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID,
                    "Galggg VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Galggg VPN")
                .setContentText("Galggg VPN active")
                .setOngoing(true)
                .build();
    }

    private String getTunDeviceArg() {
        return "fd://0";
    }

    private String getTun2SocksPath() {
        return "";
    }

    @Override
    public void onDestroy() {
        try {
            SingBoxRunner.stopAll();
        } catch (Throwable ignore) {
        }
        try {
            if (tunPfd != null) {
                tunPfd.close();
            }
        } catch (Throwable ignore) {
        } finally {
            tunPfd = null;
        }
        try {
            if (savedStdinFd != null) {
                Os.dup2(savedStdinFd, OsConstants.STDIN_FILENO);
                Os.close(savedStdinFd);
                savedStdinFd = null;
            }
            if (tunDupFd != null) {
                Os.close(tunDupFd);
                tunDupFd = null;
            }
        } catch (Throwable ignore) {
        }
        ACTIVE.set(false);
        stopForeground(true);
        super.onDestroy();
    }

    private void stopTunAndRunner() {
        try {
            SingBoxRunner.stopAll();
        } catch (Throwable ignore) {
        }
        ACTIVE.set(false);
        cleanupTunResources();
    }

    @Override
    public void onRevoke() {
        stopTunAndRunner();
        stopForeground(true);
        super.onRevoke();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopTunAndRunner();
        stopForeground(true);
        super.onTaskRemoved(rootIntent);
    }

    private void cleanupTunResources() {
        if (savedStdinFd != null) {
            try {
                Os.dup2(savedStdinFd, OsConstants.STDIN_FILENO);
            } catch (ErrnoException e) {
                Log.w("GalgggVpnService", "Failed to restore stdin", e);
            } finally {
                try {
                    Os.close(savedStdinFd);
                } catch (ErrnoException ignore) {
                }
                savedStdinFd = null;
            }
        }
        if (tunDupFd != null) {
            try {
                Os.close(tunDupFd);
            } catch (ErrnoException ignore) {
            } finally {
                tunDupFd = null;
            }
        }
        if (tunPfd != null) {
            try {
                tunPfd.close();
            } catch (Exception ignore) {
            } finally {
                tunPfd = null;
            }
        }
    }
}
