package com.example.galggg.provision;

public final class ProvisionConstants {
    private ProvisionConstants() {}
    public static final String ADDRESS   = "89.23.123.2";
    public static final int    PORT      = 443;
    public static final String UUID      = "ebc04295-af30-4e23-af6e-6ff768858527";
    public static final String SNI       = "www.cloudflare.com";
    public static final String PUBLIC_KEY= "ujUA_krY6B-kK_A_zZzqLCPo8GXEhuvAOoLizHNJyyk";  // = Password из xray x25519
    public static final String SHORT_ID  = "c9a8df23e52af90e";
    public static final String FLOW      = "xtls-rprx-vision";
    // путь для клиентского конфига Xray
    public static final String XRAY_DIR  = "xray";
    public static final String XRAY_CFG  = "config.json";
    // Порт локального SOCKS, куда подключается tun2socks
    public static final int SOCKS_PORT   = 10808;
}

