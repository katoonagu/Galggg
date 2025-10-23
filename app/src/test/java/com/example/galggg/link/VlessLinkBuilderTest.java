package com.example.galggg.link;

import static org.junit.Assert.assertTrue;

import com.example.galggg.provision.LocalProvision;
import com.example.galggg.provision.ProvisionData;

import org.junit.Test;

public class VlessLinkBuilderTest {

    @Test
    public void buildUsesProvisionConstants() {
        ProvisionData data = LocalProvision.get();
        String link = VlessLinkBuilder.build(data);

        assertTrue(link.contains("sni=www.cloudflare.com"));
        assertTrue(link.contains("pbk=ujUA_krY6B-kK_A_zZzqLCPo8GXEhuvAOoLizHNJyyk"));
        assertTrue(link.contains("sid=c9a8df23e52af90e"));
        assertTrue(link.contains("flow=xtls-rprx-vision"));
    }
}

