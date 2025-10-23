package com.example.galggg.link;

import com.example.galggg.provision.ProvisionData;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class VlessLinkBuilder {
    private VlessLinkBuilder() {}

    public static String build(ProvisionData p) {
        String label = "Galggg";
        return "vless://" + p.uuid + "@" + p.address + ":" + p.port +
                "?encryption=none&security=reality&fp=chrome" +
                "&sni=" + enc(p.sni) +
                "&pbk=" + enc(p.publicKey) +
                "&sid=" + enc(p.shortId) +
                "&type=tcp&flow=" + enc(p.flow) +
                "#" + enc(label);
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}

