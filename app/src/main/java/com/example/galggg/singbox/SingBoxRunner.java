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

        int socksPort = com.example.galggg.singbox.SocksPortAllocator.pick();
        android.util.Log.d("SingBoxRunner", "picked socks port: " + socksPort);

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
        Process sbProc = new ProcessBuilder(sbCmd).start();
        P_SB = sbProc;
        pump(P_SB, "SingBox");
        drainProcess("sing-box", P_SB);
        android.util.Log.d("SingBoxRunner", "started: " + String.join(" ", sbCmd));

        boolean listening = waitPort("127.0.0.1", socksPort, 3000);
        android.util.Log.d("SingBoxRunner", "socks listening=" + listening + " on " + socksPort);
        if (!listening) {
            throw new IllegalStateException("sing-box did not start listening on " + socksPort);
        }

        android.util.Log.d("SingBoxRunner", "device arg -> " + tunFdUri);
        java.util.List<String> t2sCmd = java.util.Arrays.asList(
                t2s.getAbsolutePath(),
                "-device", tunFdUri,
                "-mtu", "1500",
                "-proxy", "socks5://127.0.0.1:" + socksPort,
                "-tcp-auto-tuning",
                "-loglevel", "info"
        );
        Process t2sProc = new ProcessBuilder(t2sCmd).start();
        P_T2S = t2sProc;
        pump(P_T2S, "tun2socks");
        drainProcess("tun2socks", P_T2S);
        android.util.Log.d("SingBoxRunner", "started: " + String.join(" ", t2sCmd));

        new Thread(() -> {
            try {
                Thread.sleep(2500);
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .proxy(new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                                new java.net.InetSocketAddress("127.0.0.1", socksPort)))
                        .connectTimeout(java.time.Duration.ofSeconds(7))
                        .readTimeout(java.time.Duration.ofSeconds(7))
                        .writeTimeout(java.time.Duration.ofSeconds(7))
                        .build();
                try {
                    okhttp3.Response r1 = client.newCall(
                            new okhttp3.Request.Builder()
                                    .url("https://api.ipify.org")
                                    .header("Connection", "close")
                                    .build()
                    ).execute();
                    android.util.Log.d("SingBoxRunner", "selftest https ipify: " + (r1.body() != null ? r1.body().string() : "<null>"));
                } catch (Throwable t) {
                    android.util.Log.w("SingBoxRunner", "selftest https failed: " + t.getMessage());
                }
                try {
                    okhttp3.Response r2 = client.newCall(
                            new okhttp3.Request.Builder()
                                    .url("http://httpbin.org/ip")
                                    .header("Connection", "close")
                                    .header("User-Agent", "Galggg/1.0")
                                    .build()
                    ).execute();
                    android.util.Log.d("SingBoxRunner", "selftest http httpbin: " + r2.code());
                } catch (Throwable t) {
                    android.util.Log.w("SingBoxRunner", "selftest http failed: " + t.getMessage());
                }
            } catch (Throwable t) {
                android.util.Log.e("SingBoxRunner", "selftest thread failed", t);
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

    private static boolean waitPort(String host, int port, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 300);
                return true;
            } catch (Exception ignore) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static void drainProcess(String tag, Process process) {
        new Thread(() -> {
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    android.util.Log.e("SingBoxRunner", tag + " STDERR: " + line);
                }
            } catch (Exception ignored) {
            }
        }, "drain-" + tag).start();
    }
}
