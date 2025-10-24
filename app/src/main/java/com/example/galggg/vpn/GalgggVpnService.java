package com.example.galggg.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.galggg.R;
import com.example.galggg.singbox.SingBoxRunner;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GalgggVpnService extends VpnService {

    public static final String ACTION_START = "com.example.galggg.vpn.START";
    public static final String ACTION_STOP = "com.example.galggg.vpn.STOP";
    private static final String CH_ID = "galggg_vpn";
    private static final int NOTIF_ID = 101;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ParcelFileDescriptor tunPfd;
    private int tunFdInt = -1;

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
        if (tunFdInt >= 0) {
            int currentFd = tunFdInt;
            tunFdInt = -1;
            try {
                ParcelFileDescriptor.adoptFd(currentFd).close();
            } catch (IOException ignore) {
            }
        }
        VpnService.Builder b = new VpnService.Builder();
        b.setSession("Galggg");
        b.addAddress("10.0.0.2", 32);
        b.addDnsServer("1.1.1.1");
        b.addRoute("0.0.0.0", 0);
        try {
            b.addDisallowedApplication(getPackageName());
        } catch (Exception ignore) {
        }

        tunPfd = b.establish();
        if (tunPfd == null) {
            throw new IllegalStateException("establish() returned null");
        }

        ParcelFileDescriptor dupPfd = null;
        try {
            dupPfd = ParcelFileDescriptor.dup(tunPfd.getFileDescriptor());
            Os.fcntlInt(dupPfd.getFileDescriptor(), OsConstants.F_SETFD, 0);
            tunFdInt = dupPfd.detachFd();
            dupPfd = null;
        } finally {
            if (dupPfd != null) {
                try {
                    dupPfd.close();
                } catch (IOException ignore) {
                }
            }
        }
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
            String tunUri = getTunFdUri();
            String t2s = getTun2SocksPath();
            SingBoxRunner.startAll(getApplicationContext(), tunUri, t2s);
            ACTIVE.set(true);
            Log.d("GalgggVpnService", "VPN engines started");
        } catch (Throwable t) {
            Log.e("GalgggVpnService", "start failed", t);
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

    private String getTunFdUri() {
        if (tunFdInt < 0) {
            throw new IllegalStateException("tun fd is not ready");
        }
        return "fd://" + tunFdInt;
    }

    private String getTun2SocksPath() {
        return "";
    }

    @Override
    public void onDestroy() {
        stopTunAndRunner();
        stopForeground(true);
        super.onDestroy();
    }

    private void stopTunAndRunner() {
        try {
            SingBoxRunner.stopAll();
        } catch (Throwable ignore) {
        }
        ACTIVE.set(false);
        try {
            if (tunPfd != null) {
                tunPfd.close();
            }
        } catch (Throwable ignore) {
        } finally {
            tunPfd = null;
        }
        try {
            if (tunFdInt >= 0) {
                ParcelFileDescriptor.adoptFd(tunFdInt).close();
            }
        } catch (IOException ignore) {
        }
        tunFdInt = -1;
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
}
