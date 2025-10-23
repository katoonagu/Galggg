package com.example.galggg.provision;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class XrayClientConfigBuilder {
    private XrayClientConfigBuilder(){}

    public static String build() throws JSONException {
        JSONObject root = new JSONObject();

        // Логи Xray на устройстве по минимуму
        root.put("log", new JSONObject().put("loglevel", "warning"));

        // Inbound: локальный SOCKS для tun2socks
        JSONObject socks = new JSONObject();
        socks.put("tag", "socks-in");
        socks.put("listen", "127.0.0.1");
        socks.put("port", ProvisionConstants.SOCKS_PORT);
        socks.put("protocol", "socks");
        socks.put("settings", new JSONObject().put("udp", true));
        root.put("inbounds", new JSONArray().put(socks));

        // Outbound: VLESS + REALITY (Vision)
        JSONObject vless = new JSONObject();
        vless.put("tag", "to-vless");
        vless.put("protocol", "vless");

        JSONObject vnext = new JSONObject();
        vnext.put("address", ProvisionConstants.ADDRESS);
        vnext.put("port", ProvisionConstants.PORT);

        JSONObject user = new JSONObject();
        user.put("id", ProvisionConstants.UUID);
        user.put("encryption", "none");
        user.put("flow", ProvisionConstants.FLOW);
        vnext.put("users", new JSONArray().put(user));

        vless.put("settings", new JSONObject().put("vnext", new JSONArray().put(vnext)));

        JSONObject reality = new JSONObject();
        reality.put("serverName", ProvisionConstants.SNI);
        reality.put("publicKey",  ProvisionConstants.PUBLIC_KEY);
        reality.put("shortId",    ProvisionConstants.SHORT_ID);
        reality.put("fingerprint","chrome");

        JSONObject stream = new JSONObject();
        stream.put("network", "tcp");      // безопасный алиас вместо "raw" на старых бинарях
        stream.put("security", "reality");
        stream.put("realitySettings", reality);
        vless.put("streamSettings", stream);

        JSONArray outs = new JSONArray();
        outs.put(vless);
        outs.put(new JSONObject().put("tag","direct").put("protocol","freedom"));
        outs.put(new JSONObject().put("tag","block").put("protocol","blackhole"));
        root.put("outbounds", outs);

        return root.toString();
    }
}

