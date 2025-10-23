package com.example.galggg.vpn;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.example.galggg.provision.ProvisionConstants;
import com.example.galggg.vpn.shadows.ShadowOs;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = ShadowOs.class)
public class XrayRunnerRoutingTest {

    @Test
    public void configUsesProvisionConstants() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        XrayRunner runner = new XrayRunner(ctx, null);

        Method write = XrayRunner.class.getDeclaredMethod("writeXrayConfig");
        write.setAccessible(true);

        File cfgFile = (File) write.invoke(runner);
        String json = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);
        Log.d("XrayRunnerRoutingTest", "config preview: " + json);
        System.out.println("xray client config at: " + cfgFile.getAbsolutePath());

        JSONObject root = new JSONObject(json);
        JSONObject inbound = root.getJSONArray("inbounds").getJSONObject(0);
        assertEquals(ProvisionConstants.SOCKS_PORT, inbound.getInt("port"));

        JSONObject vnext = root.getJSONArray("outbounds")
                .getJSONObject(0)
                .getJSONObject("settings")
                .getJSONArray("vnext")
                .getJSONObject(0);
        assertEquals(ProvisionConstants.ADDRESS, vnext.getString("address"));
        assertEquals(ProvisionConstants.PORT, vnext.getInt("port"));

        JSONObject user = vnext.getJSONArray("users").getJSONObject(0);
        assertEquals(ProvisionConstants.UUID, user.getString("id"));
        assertEquals(ProvisionConstants.FLOW, user.getString("flow"));

        JSONObject stream = root.getJSONArray("outbounds")
                .getJSONObject(0)
                .getJSONObject("streamSettings");
        assertEquals("tcp", stream.getString("network"));
        assertEquals("reality", stream.getString("security"));

        JSONObject reality = stream.getJSONObject("realitySettings");
        assertEquals(ProvisionConstants.SNI, reality.getString("serverName"));
        assertEquals(ProvisionConstants.PUBLIC_KEY, reality.getString("publicKey"));
        assertEquals(ProvisionConstants.SHORT_ID, reality.getString("shortId"));
    }
}
