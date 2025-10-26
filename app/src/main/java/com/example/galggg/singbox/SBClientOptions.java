package com.example.galggg.singbox;

import java.util.Objects;

/**
 * Runtime parameters for sing-box Shadowsocks client configuration.
 */
public final class SBClientOptions {
    private final String serverHost;
    private final int serverPort;
    private final String method;
    private final String passwordBase64;

    public SBClientOptions(String serverHost, int serverPort, String method, String passwordBase64) {
        this.serverHost = Objects.requireNonNull(serverHost, "serverHost == null");
        this.serverPort = serverPort;
        this.method = Objects.requireNonNull(method, "method == null");
        this.passwordBase64 = Objects.requireNonNull(passwordBase64, "passwordBase64 == null");
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getMethod() {
        return method;
    }

    public String getPasswordBase64() {
        return passwordBase64;
    }

    @Override
    public String toString() {
        return "SBClientOptions{" +
                "serverHost='" + serverHost + '\'' +
                ", serverPort=" + serverPort +
                ", method='" + method + '\'' +
                '}';
    }
}
