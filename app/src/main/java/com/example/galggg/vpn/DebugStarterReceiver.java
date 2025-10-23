package com.example.galggg.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class DebugStarterReceiver extends BroadcastReceiver {
    public static final String ACTION_START = "com.example.galggg.DEBUG_START_VPN";
    public static final String ACTION_STOP = "com.example.galggg.DEBUG_STOP_VPN";
    private static final String TAG = "DebugStarterReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            Intent svc = new Intent(context, GalgggVpnService.class);
            svc.setAction("com.example.galggg.vpn.START");
            ContextCompat.startForegroundService(context, svc);
            Log.d(TAG, "DEBUG_START_VPN handled -> startForegroundService");
        } else if (ACTION_STOP.equals(action)) {
            Intent svc = new Intent(context, GalgggVpnService.class);
            svc.setAction("com.example.galggg.vpn.STOP");
            context.startService(svc);
            Log.d(TAG, "DEBUG_STOP_VPN handled -> stop Service");
        }
    }
}
