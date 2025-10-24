package com.example.galggg.singbox;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SBClientConfigBuilder {
    private SBClientConfigBuilder(){}

    public static String build(int socksPort) throws Exception {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "debug"));

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put(new JSONObject().put("address", "1.1.1.1").put("detour", "direct"));
        dns.put("servers", servers);
        root.put("dns", dns);

        JSONObject socks = new JSONObject();
        socks.put("type", "socks");
        socks.put("tag",  "socks-in");
        socks.put("listen", "127.0.0.1");
        socks.put("listen_port", socksPort);
        root.put("inbounds", new JSONArray().put(socks));

        JSONObject ss = new JSONObject();
        ss.put("type", "shadowsocks");
        ss.put("tag",  "to-ss");
        ss.put("server", SBConstants.SERVER_HOST);
        ss.put("server_port", SBConstants.SERVER_PORT);
        ss.put("method", SBConstants.METHOD);
        ss.put("password", SBConstants.PASSWORD_B64);

        JSONArray outs = new JSONArray();
        outs.put(ss);
        outs.put(new JSONObject().put("type","direct").put("tag","direct"));
        outs.put(new JSONObject().put("type","block").put("tag","block"));
        root.put("outbounds", outs);

        return root.toString();
    }
}
