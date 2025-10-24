package com.example.galggg.singbox;

import android.content.Context;

public final class SingBoxRunner {
    private static volatile Process P_SB;
    private static volatile Process P_T2S;

    private SingBoxRunner(){}

    private static void pump(Process p, String tag) {
        new Thread(() -> {
            try (java.io.BufferedReader br =
                         new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    android.util.Log.d(tag, line);
                }
            } catch (Exception ignore) {
            }
        }, tag + "-pump").start();
    }

    public static int startAll(Context ctx, String tunFdUri, String tun2socksPath) throws Exception {
        stopAll();

        int socksPort = com.example.galggg.net.PortAllocator.chooseLoopbackPort(
                com.example.galggg.singbox.SBConstants.SOCKS_PORT);

        java.io.File sb = com.example.galggg.singbox.SBBinaryPaths.singbox(ctx);
        java.io.File t2s = (tun2socksPath != null && !tun2socksPath.isEmpty())
                ? new java.io.File(tun2socksPath)
                : com.example.galggg.singbox.SBBinaryPaths.tun2socks(ctx);

        android.util.Log.d("SingBoxRunner", "using sing-box: " + sb.getAbsolutePath());
        android.util.Log.d("SingBoxRunner", "using tun2socks: " + t2s.getAbsolutePath());

        String cfg = com.example.galggg.singbox.SBClientConfigBuilder.build(socksPort);
        android.util.Log.d("SingBoxRunner", "sing-box config preview: " + cfg.substring(0, Math.min(300, cfg.length())));
        java.io.File dir = new java.io.File(ctx.getFilesDir(), com.example.galggg.singbox.SBConstants.SB_DIR);
        if (!dir.exists()) dir.mkdirs();
        java.io.File cfgFile = new java.io.File(dir, com.example.galggg.singbox.SBConstants.SB_CFG);
        try (java.io.FileOutputStream os = new java.io.FileOutputStream(cfgFile)) {
            os.write(cfg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        java.util.List<String> sbCmd = java.util.Arrays.asList(sb.getAbsolutePath(), "run", "-c", cfgFile.getAbsolutePath());
        P_SB = new ProcessBuilder(sbCmd).redirectErrorStream(true).start();
        pump(P_SB, "SingBox");
        android.util.Log.d("SingBoxRunner", "started: " + String.join(" ", sbCmd));

        java.util.List<String> t2sCmd = java.util.Arrays.asList(
                t2s.getAbsolutePath(),
                "-device", tunFdUri, "-mtu", "1500",
                "-proxy", "socks5://127.0.0.1:" + socksPort,
                "-tcp-auto-tuning", "-loglevel", "info"
        );
        P_T2S = new ProcessBuilder(t2sCmd).redirectErrorStream(true).start();
        pump(P_T2S, "tun2socks");
        android.util.Log.d("SingBoxRunner", "started: " + String.join(" ", t2sCmd));

        new Thread(() -> {
            try {
                Thread.sleep(2500);
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .proxy(new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                                new java.net.InetSocketAddress("127.0.0.1", socksPort)))
                        .build();
                okhttp3.Response r = client.newCall(new okhttp3.Request.Builder().url("https://api.ipify.org").build()).execute();
                android.util.Log.d("SingBoxRunner", "public ip via socks: " + (r.body()!=null ? r.body().string() : "<null>"));
            } catch (Throwable t) {
                android.util.Log.e("SingBoxRunner", "selftest failed", t);
            }
        }).start();
        return socksPort;
    }

    public static void stopAll() {
        try {
            if (P_T2S != null) {
                P_T2S.destroy();
                P_T2S = null;
            }
        } catch (Throwable ignore) {
        }
        try {
            if (P_SB != null) {
                P_SB.destroy();
                P_SB = null;
            }
        } catch (Throwable ignore) {
        }
    }
}
