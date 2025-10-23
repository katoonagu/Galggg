package com.example.galggg.provision;

public final class ProvisionData {
    public final String address;
    public final String uuid;
    public final String sni;
    public final String publicKey;
    public final String shortId;
    public final String flow;
    public final int port;

    public ProvisionData(String address, int port, String uuid, String sni,
                         String publicKey, String shortId, String flow) {
        this.address = address;
        this.port = port;
        this.uuid = uuid;
        this.sni = sni;
        this.publicKey = publicKey;
        this.shortId = shortId;
        this.flow = flow;
    }
}

