package com.example.galggg.singbox;

import android.content.Context;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SingBoxRunner {
    private static final Pattern SOCKS_STARTED =
            Pattern.compile("inbound/socks\\[socks-in\\]: tcp server started at .*:(\\d+)");

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

    public static int startAll(Context ctx, String tunFdUri, String tun2socksPath, SBClientOptions options) throws Exception {
        stopAll();

        int socksPort = com.example.galggg.singbox.SocksPortAllocator.pick();
        android.util.Log.d("SingBoxRunner", "picked socks port: " + socksPort);

        java.io.File sb = com.example.galggg.singbox.SBBinaryPaths.singbox(ctx);
        java.io.File t2s = (tun2socksPath != null && !tun2socksPath.isEmpty())
                ? new java.io.File(tun2socksPath)
                : com.example.galggg.singbox.SBBinaryPaths.tun2socks(ctx);

        SBClientOptions opts = (options != null) ? options : com.example.galggg.singbox.SBConstants.defaultOptions();
        android.util.Log.d("SingBoxRunner", "using sing-box: " + sb.getAbsolutePath());
        android.util.Log.d("SingBoxRunner", "using tun2socks: " + t2s.getAbsolutePath());
        android.util.Log.d("SingBoxRunner", "target server: " + opts.getServerHost() + ":" + opts.getServerPort());

        String cfg = com.example.galggg.singbox.SBClientConfigBuilder.build(socksPort, opts);
        android.util.Log.d("SingBoxRunner", "sing-box config preview: " + cfg.substring(0, Math.min(300, cfg.length())));
        java.io.File dir = new java.io.File(ctx.getFilesDir(), com.example.galggg.singbox.SBConstants.SB_DIR);
        if (!dir.exists()) dir.mkdirs();
        java.io.File cfgFile = new java.io.File(dir, com.example.galggg.singbox.SBConstants.SB_CFG);
        try (java.io.FileOutputStream os = new java.io.FileOutputStream(cfgFile)) {
            os.write(cfg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        java.util.List<String> sbCmd = java.util.Arrays.asList(sb.getAbsolutePath(), "run", "-c", cfgFile.getAbsolutePath());
        Process sbProc = new ProcessBuilder(sbCmd)
                .directory(ctx.getFilesDir())
                .start();
        P_SB = sbProc;
        pump(P_SB, "SingBox");
        CountDownLatch socksReadyLatch = new CountDownLatch(1);
        drainProcess("sing-box", P_SB, socksReadyLatch, socksPort);
        android.util.Log.d("SingBoxRunner", "started: " + String.join(" ", sbCmd));

        boolean ready = socksReadyLatch.await(3, TimeUnit.SECONDS);
        if (!ready) {
            ready = probeSocks("127.0.0.1", socksPort, 2000);
        }
        android.util.Log.d("SingBoxRunner", "socks listening=" + ready + " on " + socksPort);
        if (!ready) {
            android.util.Log.w("SingBoxRunner", "sing-box SOCKS port " + socksPort + " not confirmed; continuing with tun2socks");
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
        Process t2sProc = new ProcessBuilder(t2sCmd)
                .directory(ctx.getFilesDir())
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();
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

    private static boolean probeSocks(String host, int port, int timeoutMs) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.getOutputStream().write(new byte[]{0x05, 0x01, 0x00});
            int ver = socket.getInputStream().read();
            int method = socket.getInputStream().read();
            return ver == 0x05;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drainProcess(String tag, Process process) {
        drainProcess(tag, process, null, -1);
    }

    private static void drainProcess(String tag, Process process,
                                     CountDownLatch socksUp, int socksPort) {
        new Thread(() -> {
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    android.util.Log.e("SingBoxRunner", tag + " STDERR: " + line);
                    if ("sing-box".equals(tag) && socksUp != null) {
                        Matcher matcher = SOCKS_STARTED.matcher(line);
                        if (matcher.find()) {
                            try {
                                int port = Integer.parseInt(matcher.group(1));
                                if (port == socksPort) {
                                    socksUp.countDown();
                                }
                            } catch (NumberFormatException ignore) {
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "drain-" + tag).start();
    }
}
