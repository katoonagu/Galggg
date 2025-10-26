package com.example.galggg.singbox;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SBClientConfigBuilder {
    private SBClientConfigBuilder(){}

    public static String build(int socksPort, SBClientOptions options) throws Exception {
        SBClientOptions opts = options != null ? options : SBConstants.defaultOptions();
        JSONObject log = new JSONObject()
                .put("level", (com.example.galggg.BuildConfig.DEBUG ? "debug" : "warn"));

        JSONArray dnsServers = new JSONArray()
                .put(new JSONObject().put("tag", "dns1").put("address", "udp://1.1.1.1"))
                .put(new JSONObject().put("tag", "dns2").put("address", "udp://9.9.9.9"));
        JSONObject dns = new JSONObject()
                .put("servers", dnsServers)
                .put("strategy", "prefer_ipv4");

        JSONObject socks = new JSONObject()
                .put("type", "socks")
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", socksPort)
                .put("udp_timeout", "5m");
        JSONArray inbounds = new JSONArray().put(socks);

        JSONObject ss = new JSONObject()
                .put("type", "shadowsocks")
                .put("tag", "to-ss")
                .put("server", opts.getServerHost())
                .put("server_port", opts.getServerPort())
                .put("method", opts.getMethod())
                .put("password", opts.getPasswordBase64());

        JSONArray outbounds = new JSONArray()
                .put(ss)
                .put(new JSONObject().put("type", "direct").put("tag", "direct"))
                .put(new JSONObject().put("type", "block").put("tag", "block"));

        JSONObject route = new JSONObject()
                .put("default_domain_resolver", "dns1");

        JSONObject root = new JSONObject()
                .put("log", log)
                .put("dns", dns)
                .put("route", route)
                .put("inbounds", inbounds)
                .put("outbounds", outbounds);

        return root.toString();
    }
}
