package com.example.galggg.provision;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class XrayClientConfigBuilder {
    private XrayClientConfigBuilder() {}

    public static String build(ProvisionData data) throws JSONException {
        JSONObject root = new JSONObject();

        root.put("log", new JSONObject().put("loglevel", "warning"));

        JSONObject socks = new JSONObject();
        socks.put("tag", "socks-in");
        socks.put("listen", "127.0.0.1");
        socks.put("port", ProvisionConstants.SOCKS_PORT);
        socks.put("protocol", "socks");
        socks.put("settings", new JSONObject().put("udp", true));
        root.put("inbounds", new JSONArray().put(socks));

        JSONObject vless = new JSONObject();
        vless.put("tag", "to-vless");
        vless.put("protocol", "vless");

        JSONObject vnext = new JSONObject();
        vnext.put("address", data.address);
        vnext.put("port", data.port);

        JSONObject user = new JSONObject();
        user.put("id", data.uuid);
        user.put("encryption", "none");
        user.put("flow", data.flow);
        vnext.put("users", new JSONArray().put(user));

        vless.put("settings", new JSONObject().put("vnext", new JSONArray().put(vnext)));

        JSONObject reality = new JSONObject();
        reality.put("serverName", data.sni);
        reality.put("publicKey", data.publicKey);
        reality.put("shortId", data.shortId);
        reality.put("fingerprint", "chrome");

        JSONObject stream = new JSONObject();
        stream.put("network", "tcp");
        stream.put("security", "reality");
        stream.put("realitySettings", reality);
        vless.put("streamSettings", stream);

        JSONArray outs = new JSONArray();
        outs.put(vless);
        outs.put(new JSONObject().put("tag", "direct").put("protocol", "freedom"));
        outs.put(new JSONObject().put("tag", "block").put("protocol", "blackhole"));
        root.put("outbounds", outs);

        return root.toString();
    }
}

