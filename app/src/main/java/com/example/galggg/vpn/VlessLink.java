package com.example.galggg.vpn;

import android.net.Uri;

import java.util.Locale;

public class VlessLink {
    public final String uuid, host, sni, pbk, sid, flow, fp, security, type;
    public final int port;

    public VlessLink(String uuid, String host, int port, String sni, String pbk, String sid,
                     String flow, String fp, String security, String type) {
        this.uuid = uuid;
        this.host = host;
        this.port = port;
        this.sni = sni;
        this.pbk = pbk;
        this.sid = sid;
        this.flow = flow;
        this.fp = fp;
        this.security = security;
        this.type = type;
    }

    public static VlessLink parse(String vless) {
        if (vless == null || !vless.toLowerCase(Locale.ROOT).startsWith("vless://")) return null;
        Uri u = Uri.parse(vless);
        String userInfo = u.getUserInfo();
        if (userInfo == null) return null;
        String uuid = userInfo;
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 443;
        String sni = u.getQueryParameter("sni");
        String pbk = u.getQueryParameter("pbk");
        String sid = u.getQueryParameter("sid");
        String flow = u.getQueryParameter("flow");
        String fp = u.getQueryParameter("fp");
        String security = u.getQueryParameter("security");
        String type = u.getQueryParameter("type");
        return new VlessLink(uuid, host, port, sni, pbk, sid, flow, fp, security, type);
    }
}
