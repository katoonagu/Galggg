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
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.galggg.R;
import com.example.galggg.singbox.SBClientOptions;
import com.example.galggg.singbox.SBConstants;
import com.example.galggg.singbox.SingBoxRunner;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GalgggVpnService extends VpnService {

    public static final String ACTION_START = "com.example.galggg.vpn.START";
    public static final String ACTION_STOP = "com.example.galggg.vpn.STOP";
    public static final String ACTION_STATUS = "com.example.galggg.vpn.STATUS";

    public static final String EXTRA_STATE = "com.example.galggg.vpn.EXTRA_STATE";
    public static final String EXTRA_SERVER_ID = "com.example.galggg.vpn.EXTRA_SERVER_ID";
    public static final String EXTRA_PROTOCOL = "com.example.galggg.vpn.EXTRA_PROTOCOL";
    public static final String EXTRA_SERVER_HOST = "com.example.galggg.vpn.EXTRA_SERVER_HOST";
    public static final String EXTRA_SERVER_PORT = "com.example.galggg.vpn.EXTRA_SERVER_PORT";
    public static final String EXTRA_SERVER_METHOD = "com.example.galggg.vpn.EXTRA_SERVER_METHOD";
    public static final String EXTRA_SERVER_PASSWORD = "com.example.galggg.vpn.EXTRA_SERVER_PASSWORD";
    public static final String EXTRA_ERROR = "com.example.galggg.vpn.EXTRA_ERROR";

    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_CONNECTED = "CONNECTED";
    public static final String STATE_DISCONNECTED = "DISCONNECTED";
    public static final String STATE_ERROR = "ERROR";

    private static final String CH_ID = "galggg_vpn";
    private static final int NOTIF_ID = 101;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ParcelFileDescriptor tunPfd;
    private FileDescriptor tunDupFd;
    private FileDescriptor savedStdinFd;
    private String activeServerId;
    private String activeProtocol;
    private SBClientOptions activeOptions = SBConstants.defaultOptions();

    public static boolean isActive() {
        return ACTIVE.get();
    }

    private SBClientOptions resolveOptionsFromIntent(Intent intent) {
        if (intent == null) {
            return SBConstants.defaultOptions();
        }
        String host = intent.getStringExtra(EXTRA_SERVER_HOST);
        int port = intent.getIntExtra(EXTRA_SERVER_PORT, -1);
        String method = intent.getStringExtra(EXTRA_SERVER_METHOD);
        String password = intent.getStringExtra(EXTRA_SERVER_PASSWORD);
        if (TextUtils.isEmpty(host) || port <= 0 || TextUtils.isEmpty(method) || TextUtils.isEmpty(password)) {
            return SBConstants.defaultOptions();
        }
        return new SBClientOptions(host, port, method, password);
    }

    private void broadcastState(String state, String error) {
        Intent status = new Intent(ACTION_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_STATE, state);
        if (activeServerId != null) {
            status.putExtra(EXTRA_SERVER_ID, activeServerId);
        }
        if (activeProtocol != null) {
            status.putExtra(EXTRA_PROTOCOL, activeProtocol);
        }
        if (activeOptions != null) {
            status.putExtra(EXTRA_SERVER_HOST, activeOptions.getServerHost());
            status.putExtra(EXTRA_SERVER_PORT, activeOptions.getServerPort());
        }
        if (!TextUtils.isEmpty(error)) {
            status.putExtra(EXTRA_ERROR, error);
        }
        sendBroadcast(status);
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
        activeServerId = intent != null ? intent.getStringExtra(EXTRA_SERVER_ID) : null;
        activeProtocol = intent != null ? intent.getStringExtra(EXTRA_PROTOCOL) : null;
        activeOptions = resolveOptionsFromIntent(intent);
        broadcastState(STATE_CONNECTING, null);

        startForeground(NOTIF_ID, buildNotification());
        try {
            prepareTunOrThrow();
            remapTunToStdin();
            String tunUri = getTunDeviceArg();
            String t2s = getTun2SocksPath();
            SingBoxRunner.startAll(getApplicationContext(), tunUri, t2s, activeOptions);
            ACTIVE.set(true);
            Log.d("GalgggVpnService", "VPN engines started");
            broadcastState(STATE_CONNECTED, null);
        } catch (Throwable t) {
            Log.e("GalgggVpnService", "start failed", t);
            ACTIVE.set(false);
            broadcastState(STATE_ERROR, t.getMessage());
            stopTunAndRunner();
            stopForeground(true);
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

    private String getTunDeviceArg() {
        return "fd://0";
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
        cleanupTunResources();
        broadcastState(STATE_DISCONNECTED, null);
        activeOptions = SBConstants.defaultOptions();
        activeServerId = null;
        activeProtocol = null;
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
