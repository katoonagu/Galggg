package com.example.galggg.link;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

public final class ExternalClients {
    private ExternalClients() {}

    private static final String[] CANDIDATES = new String[] {
            "com.v2ray.ang",
            "io.nekohasekai.nekobox",
            "io.nekohasekai.nekobox.forandroid"
    };

    public static void openProfile(Context ctx, String vlessUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(vlessUri));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = ctx.getPackageManager();

        for (String pkg : CANDIDATES) {
            try {
                pm.getPackageInfo(pkg, 0);
                intent.setPackage(pkg);
                ctx.startActivity(intent);
                return;
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }

        if (intent.resolveActivity(pm) != null) {
            ctx.startActivity(intent);
            return;
        }

        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.v2ray.ang"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.v2ray.ang"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        Toast.makeText(ctx,
                "Install v2rayNG or NekoBox to complete the connection",
                Toast.LENGTH_LONG).show();
    }
}