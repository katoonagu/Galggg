package com.example.galggg.singbox;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SBClientConfigBuilder {
    private SBClientConfigBuilder(){}

    public static String build(int socksPort) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject();
        root.put("log", new org.json.JSONObject().put("level", "warn"));

        org.json.JSONObject socks = new org.json.JSONObject();
        socks.put("type", "socks");
        socks.put("tag",  "socks-in");
        socks.put("listen", "127.0.0.1");
        socks.put("listen_port", socksPort);
        root.put("inbounds", new org.json.JSONArray().put(socks));

        org.json.JSONObject ss = new org.json.JSONObject();
        ss.put("type", "shadowsocks");
        ss.put("tag",  "to-ss");
        ss.put("server", com.example.galggg.singbox.SBConstants.SERVER_HOST);
        ss.put("server_port", com.example.galggg.singbox.SBConstants.SERVER_PORT);
        ss.put("method", com.example.galggg.singbox.SBConstants.METHOD);
        ss.put("password", com.example.galggg.singbox.SBConstants.PASSWORD_B64);

        org.json.JSONArray outs = new org.json.JSONArray();
        outs.put(ss);
        outs.put(new org.json.JSONObject().put("type","direct").put("tag","direct"));
        outs.put(new org.json.JSONObject().put("type","block").put("tag","block"));
        root.put("outbounds", outs);

        return root.toString();
    }
}
