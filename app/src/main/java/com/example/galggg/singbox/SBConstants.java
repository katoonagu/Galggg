package com.example.galggg.singbox;

public final class SBConstants {
    private SBConstants() {}

    // VPS sing-box (Shadowsocks-2022 inbound)
    public static final String SERVER_HOST = "89.23.123.2";
    public static final int    SERVER_PORT = 443;

    // Method and base64 password exactly as on the server (/etc/sing-box/config.json)
    public static final String METHOD      = "2022-blake3-aes-256-gcm";
    public static final String PASSWORD_B64= "I0xjJiREXTrYlFRrK7e0rFNq1o9SvlTGVuhi1KhVKrA=";

    // Client configuration storage paths
    public static final String SB_DIR      = "singbox";
    public static final String SB_CFG      = "config.json";
}
