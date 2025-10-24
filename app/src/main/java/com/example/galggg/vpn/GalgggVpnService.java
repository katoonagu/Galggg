package com.example.galggg.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.galggg.R;
import com.example.galggg.singbox.SingBoxRunner;

import java.util.concurrent.atomic.AtomicBoolean;

public class GalgggVpnService extends VpnService {

    public static final String ACTION_START = "com.example.galggg.vpn.START";
    public static final String ACTION_STOP = "com.example.galggg.vpn.STOP";
    private static final String CH_ID = "galggg_vpn";
    private static final int NOTIF_ID = 101;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ParcelFileDescriptor tunFd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public static boolean isActive() {
        return ACTIVE.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, buildNotification());
    }

    private void prepareTunOrThrow() {
        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (Exception ignore) {
            }
            tunFd = null;
        }
        android.net.VpnService.Builder b = new android.net.VpnService.Builder();
        b.setSession("Galggg");
        b.setMtu(1500);
        b.addAddress("10.0.0.2", 32);
        b.addDnsServer("1.1.1.1");
        b.addDnsServer("8.8.8.8");
        b.addRoute("0.0.0.0", 0);
        try {
            b.addDisallowedApplication(getPackageName());
        } catch (Exception ignore) {
        }
        this.tunFd = b.establish();
        if (this.tunFd == null) {
            throw new IllegalStateException("establish() returned null");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopEngines();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running.get()) {
            Log.d("GalgggVpnService", "Start requested but already running");
            return START_STICKY;
        }
        try {
            prepareTunOrThrow();
            String tunUri = getTunFdUri();
            String t2s = getTun2SocksPath();
            SingBoxRunner.startAll(getApplicationContext(), tunUri, t2s);
            running.set(true);
            ACTIVE.set(true);
            Log.d("GalgggVpnService", "VPN engines started");
        } catch (Throwable t) {
            Log.e("GalgggVpnService", "start failed", t);
            stopEngines();
            stopSelf();
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
        ParcelFileDescriptor currentTun = tunFd;
        if (currentTun == null) {
            throw new IllegalStateException("TUN descriptor is not available");
        }
        return "fd://" + currentTun.getFd();
    }

    private String getTun2SocksPath() {
        return "";
    }

    @Override
    public void onDestroy() {
        stopEngines();
        stopForeground(true);
        super.onDestroy();
    }

    private void stopEngines() {
        SingBoxRunner.stopAll();
        running.set(false);
        ACTIVE.set(false);
        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (Exception ignore) {
            }
            tunFd = null;
        }
    }

    @Override
    public void onRevoke() {
        stopEngines();
        stopForeground(true);
        super.onRevoke();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopEngines();
        stopForeground(true);
        super.onTaskRemoved(rootIntent);
    }
}
