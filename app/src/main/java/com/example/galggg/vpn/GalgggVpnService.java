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

import java.util.concurrent.atomic.AtomicBoolean;

public class GalgggVpnService extends VpnService {

    public static final String ACTION_START = "com.example.galggg.vpn.START";
    public static final String ACTION_STOP = "com.example.galggg.vpn.STOP";
    private static final String CH_ID = "galggg_vpn";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ParcelFileDescriptor tun;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private XrayRunner runner;

    public static boolean isActive() {
        return ACTIVE.get();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(act)) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(1, buildNotif("Galggg: initializing..."));
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        if (running.get()) return;
        try {
            Builder b = new Builder();
            b.setSession("Galggg");
            b.addAddress("10.8.0.2", 32);
            b.addRoute("0.0.0.0", 0);
            b.addDnsServer("1.1.1.1");
            b.addDnsServer("8.8.8.8");
            tun = b.establish();
            if (tun == null) throw new IllegalStateException("Failed to establish TUN interface");

            String link = getSharedPreferences("vless_store", MODE_PRIVATE)
                    .getString("vless_link", null);
            VlessLink vl = VlessLink.parse(link);
            if (vl == null) throw new IllegalStateException("VLESS link missing or invalid");

            runner = new XrayRunner(this);
            runner.startAll(tun.getFd(), vl);

            running.set(true);
            ACTIVE.set(true);
            updateNotif("Galggg VPN running");
        } catch (Exception e) {
            Log.e("GalgggVpnService", "startVpn error", e);
            updateNotif("VPN error: " + e.getMessage());
            stopRunner();
            stopForeground(true);
            stopSelf();
        }
    }

    private void stopRunner() {
        if (runner != null) {
            runner.stopAll();
            runner = null;
        }
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception ignored) {
            }
            tun = null;
        }
        running.set(false);
        ACTIVE.set(false);
    }

    private void stopVpn() {
        if (!running.get()) {
            stopRunner();
            stopForeground(true);
            return;
        }
        stopRunner();
        stopForeground(true);
    }

    private Notification buildNotif(String text) {
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
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotif(String text) {
        startForeground(1, buildNotif(text));
    }

    @Override
    public void onDestroy() {
        stopRunner();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopRunner();
        stopForeground(true);
        super.onRevoke();
    }
}
